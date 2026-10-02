package dev.iaonnis.bluemarks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The only place that knows about Folia. On Folia there is no main thread: world/entity access must run on the
 * region that owns it, and {@code BukkitScheduler} throws. On Paper/Spigot we fall back to the normal scheduler.
 * (The Folia scheduler methods are only ever called when {@link #FOLIA} is true, so older servers never load them.)
 */
public final class Sched {

    public static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private Sched() {
    }

    /** Run {@code task} on the thread that owns the block column (x, z) of {@code world}. */
    public static void runAt(Plugin plugin, World world, int blockX, int blockZ, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().execute(plugin, world, blockX >> 4, blockZ >> 4, task);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Run {@code task} on the thread that owns {@code player} (so {@code player.getLocation()} is safe inside it). */
    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (FOLIA) {
            player.getScheduler().run(plugin, t -> task.run(), null);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Run {@code task} once after {@code delayTicks} on the global/main thread. */
    public static void later(Plugin plugin, long delayTicks, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /** Repeat {@code task} on the global/main thread. Returns a handle that cancels it. */
    public static Runnable repeat(Plugin plugin, long periodTicks, Runnable task) {
        if (FOLIA) {
            var handle = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), periodTicks, periodTicks);
            return handle::cancel;
        }
        BukkitTask handle = Bukkit.getScheduler().runTaskTimer(plugin, task, periodTicks, periodTicks);
        return handle::cancel;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
