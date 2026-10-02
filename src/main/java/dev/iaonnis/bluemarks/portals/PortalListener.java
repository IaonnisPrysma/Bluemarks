/*
 * Ported into Bluemarks from BlueMapPortalMarkers by Max Aldis
 * (https://github.com/maldis018/BlueMapPortalMarkers), licensed GPL-3.0. Modified for Bluemarks.
 */
package dev.iaonnis.bluemarks.portals;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the {@link PortalStore} and BlueMap in sync with the live world: new portals, broken portals and
 * freshly loaded chunks.
 *
 * <p>Folia: every event here fires on the region thread that owns the block/chunk, which is exactly where the
 * world access (POI query, block reads) is allowed. The store and BlueMap marker sets are thread-safe, and the
 * per-chunk bookkeeping uses concurrent collections because several regions can fire events at once.</p>
 */
public final class PortalListener implements Listener {

    private final PortalManager manager;
    private final PortalStore store;
    private final PortalBridge bridge;
    private final PoiSweeper sweeper;
    private final Log log;

    /** Packed keys of chunks already POI-queried, per world; entries are dropped on chunk unload. */
    private final Map<UUID, Set<Long>> queriedChunks = new ConcurrentHashMap<>();

    public PortalListener(PortalManager manager, PortalStore store, PortalBridge bridge, PoiSweeper sweeper, Log log) {
        this.manager = manager;
        this.store = store;
        this.bridge = bridge;
        this.sweeper = sweeper;
        this.log = log;
    }

    private static long packChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    /** Register a newly lit / paired portal. API-created portals are picked up by the chunk-load query instead. */
    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        PortalCreateEvent.CreateReason reason = e.getReason();
        if (reason != PortalCreateEvent.CreateReason.FIRE && reason != PortalCreateEvent.CreateReason.NETHER_PAIR) {
            return;
        }
        World world = e.getWorld();
        if (!manager.worldFilter().allows(world.getName())) {
            return;
        }
        List<int[]> blocks = new ArrayList<>();
        for (BlockState bs : e.getBlocks()) {
            if (bs.getType() == Material.NETHER_PORTAL) {
                blocks.add(new int[] { bs.getX(), bs.getY(), bs.getZ() });
            }
        }
        boolean anyAdded = false;
        for (PortalClustering.Cluster cluster : PortalClustering.cluster(blocks)) {
            Portal portal = new Portal(world.getUID(), world.getName(),
                    cluster.centroid()[0], cluster.centroid()[1], cluster.centroid()[2],
                    cluster.min()[0], cluster.min()[1], cluster.min()[2],
                    cluster.max()[0], cluster.max()[1], cluster.max()[2]);
            if (store.add(portal)) {
                bridge.addPortal(portal);
                anyAdded = true;
                log.info("Registered new nether portal: " + portal);
            }
        }
        if (anyAdded) {
            manager.requestSave();
        }
    }

    /** Breaking any portal block collapses the whole portal in vanilla; match it against the stored frame boxes. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.NETHER_PORTAL || !manager.worldFilter().allows(block.getWorld().getName())) {
            return;
        }
        Portal removed = store.removeContaining(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (removed != null) {
            bridge.removePortal(removed);
            manager.requestSave();
            log.info("Removed broken nether portal: " + removed);
        }
    }

    /** Opportunistic POI query of a freshly loaded chunk (needs the Paper 26.1+ POI API). */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!manager.scanOnChunkLoad() || !PoiSweeper.available()) {
            return;
        }
        World world = e.getChunk().getWorld();
        if (!manager.worldFilter().allows(world.getName())) {
            return;
        }
        Set<Long> chunks = queriedChunks.computeIfAbsent(world.getUID(), id -> ConcurrentHashMap.newKeySet());
        if (!chunks.add(packChunkKey(e.getChunk().getX(), e.getChunk().getZ()))) {
            return;
        }
        try {
            List<Portal> added = sweeper.queryChunk(e.getChunk(), PoiSweeper.CHUNK_QUERY_RADIUS);
            if (!added.isEmpty()) {
                for (Portal portal : added) {
                    bridge.addPortal(portal);
                }
                manager.requestSave();
                log.debug("Discovered " + added.size() + " portal(s) on chunk load in " + world.getName());
            }
        } catch (RuntimeException ex) {
            log.warn("Portal chunk query failed in " + world.getName(), ex);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Set<Long> chunks = queriedChunks.get(e.getChunk().getWorld().getUID());
        if (chunks != null) {
            chunks.remove(packChunkKey(e.getChunk().getX(), e.getChunk().getZ()));
        }
    }

    /** Worlds loaded after startup (e.g. via Multiverse) need their dimension recorded for portal linking. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        manager.registerWorldDimension(e.getWorld());
    }
}
