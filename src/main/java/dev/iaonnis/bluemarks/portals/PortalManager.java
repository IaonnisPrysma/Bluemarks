/*
 * Ported into Bluemarks from BlueMapPortalMarkers (NetherPortalMarkersPlugin) by Max Aldis
 * (https://github.com/maldis018/BlueMapPortalMarkers), licensed GPL-3.0. Modified for Bluemarks.
 */
package dev.iaonnis.bluemarks.portals;

import de.bluecolored.bluemap.api.BlueMapAPI;
import dev.iaonnis.bluemarks.Bluemarks;
import dev.iaonnis.bluemarks.Sched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * The portal feature of Bluemarks: wires the {@link PortalStore}, {@link PortalBridge}, {@link PoiSweeper} and
 * {@link PortalListener} together, runs sweeps, and persists the store to {@code portals.json}.
 *
 * <p>Folia: sweeps never assume a main thread. Each sweep is split into jobs that {@link Sched} runs on the
 * thread owning that spawn/player location; the result is reported through a callback once all jobs finished.</p>
 */
public final class PortalManager {

    private final Bluemarks plugin;
    private final Log log;
    private final PortalStore store = new PortalStore();
    private final PortalBridge bridge;
    private final PoiSweeper sweeper;
    private final PortalListener listener;
    private final File storageFile;
    private final Map<String, PortalLinker.Dimension> dimensions = new ConcurrentHashMap<>();

    private volatile WorldFilter worldFilter;
    private volatile int sweepRadius;
    private volatile boolean scanOnChunkLoad;
    private volatile boolean savePending;
    private Runnable cancelBackgroundSweep;
    private int nextPlayerIdx;

    /** One sweep unit: a world location, or a player whose location is read on their own thread. */
    private record Job(World world, Player player, double x, double y, double z, boolean column) {
    }

    public PortalManager(Bluemarks plugin) {
        this.plugin = plugin;
        this.log = new Log(plugin.getLogger(), plugin.getConfig().getBoolean("debug", false));
        this.storageFile = new File(plugin.getDataFolder(), "portals.json");
        this.bridge = new PortalBridge(store, log, dimensions);
        this.sweeper = new PoiSweeper(log, store);
        this.listener = new PortalListener(this, store, bridge, sweeper, log);
    }

    public void enable() {
        store.load(storageFile, log);
        refreshDimensions();
        applyConfig();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        if (PoiSweeper.available()) {
            // 2s delay to let worlds/players settle
            Sched.later(plugin, 40L, () -> sweepDefault(sweepRadius,
                    added -> log.info("Upfront POI sweep added " + added + " new portal(s).")));
        } else {
            log.info("Paper POI API not found (needs Paper 26.1+): only portals lit or broken while the server runs "
                    + "are tracked, existing portals can't be discovered.");
        }
        log.info("Portal markers enabled (" + store.size() + " stored portal(s)).");
    }

    public void disable() {
        if (cancelBackgroundSweep != null) {
            cancelBackgroundSweep.run();
        }
        store.save(storageFile, log);
    }

    /** Called from Bluemarks' single BlueMap-enable hook, after the web assets and icons are installed. */
    public void onBlueMapEnable(BlueMapAPI api) {
        bridge.rebuild(api);
    }

    // --- config ---

    private ConfigurationSection section() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("portals");
        return s != null ? s : new MemoryConfiguration();
    }

    /** Applies every hot-reloadable setting. Used on enable and by reload. */
    private void applyConfig() {
        ConfigurationSection c = section();
        log.setDebug(plugin.getConfig().getBoolean("debug", false));
        bridge.configure(c);
        sweepRadius = c.getInt("discovery.sweep-radius", 256);
        scanOnChunkLoad = c.getBoolean("discovery.scan-on-chunk-load", true);
        worldFilter = buildWorldFilter(c);
        rescheduleBackgroundSweep(c);
    }

    /** Re-read config.yml and apply it. Returns a short report for the reload command. */
    public List<String> reload() {
        plugin.reloadConfig();
        applyConfig();
        refreshDimensions();
        bridge.refresh();
        ConfigurationSection c = section();
        List<String> report = new ArrayList<>();
        report.add("portals.* appearance and linking applied");
        report.add("discovery.sweep-radius = " + sweepRadius);
        report.add("discovery.scan-on-chunk-load = " + scanOnChunkLoad + " (already-loaded chunks are not rescanned)");
        report.add("discovery.worlds." + worldFilter.mode().name().toLowerCase() + " applied");
        report.add("discovery.background-sweep.enabled = " + c.getBoolean("discovery.background-sweep.enabled", false));
        report.add("debug = " + log.isDebug());
        return report;
    }

    private static WorldFilter buildWorldFilter(ConfigurationSection c) {
        WorldFilter.Mode mode;
        try {
            mode = WorldFilter.Mode.valueOf(c.getString("discovery.worlds.mode", "blacklist").toUpperCase());
        } catch (IllegalArgumentException e) {
            mode = WorldFilter.Mode.BLACKLIST;
        }
        return new WorldFilter(mode, c.getStringList("discovery.worlds.list"));
    }

    // --- dimensions (for portal linking) ---

    public void refreshDimensions() {
        dimensions.clear();
        for (World world : Bukkit.getWorlds()) {
            registerWorldDimension(world);
        }
    }

    public void registerWorldDimension(World world) {
        dimensions.put(world.getName(), switch (world.getEnvironment()) {
            case NORMAL -> PortalLinker.Dimension.OVERWORLD;
            case NETHER -> PortalLinker.Dimension.NETHER;
            default -> PortalLinker.Dimension.OTHER;
        });
    }

    // --- sweeping ---

    /** Sweep around every allowed world's spawn and every online player. */
    public void sweepDefault(int radius, IntConsumer done) {
        List<Job> jobs = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (worldFilter.allows(world.getName())) {
                Location spawn = world.getSpawnLocation();
                jobs.add(new Job(world, null, spawn.getX(), spawn.getY(), spawn.getZ(), false));
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            jobs.add(new Job(null, player, 0, 0, 0, false));
        }
        run(jobs, radius, done);
    }

    /** Sweep around one player. */
    public void sweepPlayer(Player player, int radius, IntConsumer done) {
        run(List.of(new Job(null, player, 0, 0, 0, false)), radius, done);
    }

    /** Full-height column sweep at world coordinates. */
    public void sweepColumn(World world, double x, double z, int radius, IntConsumer done) {
        run(List.of(new Job(world, null, x, 0, z, true)), radius, done);
    }

    private void run(List<Job> jobs, int radius, IntConsumer done) {
        if (jobs.isEmpty()) {
            done.accept(0);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(jobs.size());
        AtomicInteger added = new AtomicInteger();
        for (Job job : jobs) {
            Runnable finish = () -> {
                if (remaining.decrementAndGet() == 0) {
                    done.accept(added.get());
                }
            };
            if (job.player() != null) {
                Player player = job.player();
                Sched.runForPlayer(plugin, player, () -> {
                    try {
                        Location loc = player.getLocation();
                        if (loc.getWorld() != null && worldFilter.allows(loc.getWorld().getName())) {
                            added.addAndGet(addAndSave(sweeper.sweep(loc.getWorld(), loc, radius)));
                        }
                    } catch (RuntimeException ex) {
                        log.warn("Portal sweep failed around " + player.getName(), ex);
                    } finally {
                        finish.run();
                    }
                });
            } else {
                World world = job.world();
                Sched.runAt(plugin, world, (int) Math.floor(job.x()), (int) Math.floor(job.z()), () -> {
                    try {
                        List<Portal> found = job.column()
                                ? sweeper.sweepColumn(world, job.x(), job.z(), radius)
                                : sweeper.sweep(world, new Location(world, job.x(), job.y(), job.z()), radius);
                        added.addAndGet(addAndSave(found));
                    } catch (RuntimeException ex) {
                        log.warn("Portal sweep failed in " + world.getName(), ex);
                    } finally {
                        finish.run();
                    }
                });
            }
        }
    }

    private int addAndSave(List<Portal> portals) {
        for (Portal portal : portals) {
            bridge.addPortal(portal);
        }
        if (!portals.isEmpty()) {
            requestSave();
        }
        return portals.size();
    }

    private void rescheduleBackgroundSweep(ConfigurationSection c) {
        if (cancelBackgroundSweep != null) {
            cancelBackgroundSweep.run();
            cancelBackgroundSweep = null;
        }
        if (!PoiSweeper.available() || !c.getBoolean("discovery.background-sweep.enabled", false)) {
            return;
        }
        int intervalSeconds = Math.max(30, c.getInt("discovery.background-sweep.interval-seconds", 300));
        int maxPlayers = Math.max(1, c.getInt("discovery.background-sweep.max-players-per-pass", 3));
        cancelBackgroundSweep = Sched.repeat(plugin, intervalSeconds * 20L, () -> backgroundSweep(maxPlayers));
        log.info("Background sweep scheduled every " + intervalSeconds + "s (max " + maxPlayers + " player(s)/pass).");
    }

    private void backgroundSweep(int maxPlayers) {
        List<Job> jobs = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (worldFilter.allows(world.getName())) {
                Location spawn = world.getSpawnLocation();
                jobs.add(new Job(world, null, spawn.getX(), spawn.getY(), spawn.getZ(), false));
            }
        }
        // rotating window over the online players so all of them are covered across passes
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < Math.min(maxPlayers, online.size()); i++) {
            jobs.add(new Job(null, online.get(nextPlayerIdx++ % online.size()), 0, 0, 0, false));
        }
        run(jobs, sweepRadius, added -> log.debug("Background sweep added " + added + " new portal(s)."));
    }

    // --- persistence ---

    /** Coalesced, asynchronous save: a burst of requests collapses into one write. */
    public void requestSave() {
        if (savePending) {
            return;
        }
        savePending = true;
        CompletableFuture.runAsync(() -> {
            savePending = false;
            store.save(storageFile, log);
        });
    }

    // --- accessors ---

    public PortalStore store() {
        return store;
    }

    public PortalBridge bridge() {
        return bridge;
    }

    public WorldFilter worldFilter() {
        return worldFilter;
    }

    public boolean scanOnChunkLoad() {
        return scanOnChunkLoad;
    }

    public int sweepRadius() {
        return sweepRadius;
    }
}
