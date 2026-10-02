/*
 * Ported into Bluemarks from BlueMapPortalMarkers by Max Aldis
 * (https://github.com/maldis018/BlueMapPortalMarkers), licensed GPL-3.0. Modified for Bluemarks.
 */
package dev.iaonnis.bluemarks.portals;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Discovers existing nether portals through Paper's point-of-interest (POI) API and feeds new ones into the
 * {@link PortalStore}.
 *
 * <p>The POI API only exists on Paper 26.1+, while Bluemarks is compiled against 1.19.4 so it keeps running on
 * older servers. The API is therefore reached by reflection; {@link #available()} tells callers whether it is
 * there. Without it, only portals lit/broken while the server runs are tracked.</p>
 *
 * <p>Every method touches world data, so it must run on the thread that owns the location (main thread on
 * Paper, the owning region on Folia, see {@code Sched}).</p>
 */
public final class PoiSweeper {

    /** Default radius for the cheap per-chunk query. */
    public static final int CHUNK_QUERY_RADIUS = 16;

    private static final Method LOCATE;       // World#locateAllPoiInRange(Location, Predicate, int, Occupancy)
    private static final Method RESULT_LOC;   // PoiSearchResult#location()
    private static final Object NETHER_PORTAL; // PoiTypes.NETHER_PORTAL
    private static final Object ANY;          // PoiType.Occupancy.ANY

    static {
        Method locate = null;
        Method resultLoc = null;
        Object portalType = null;
        Object any = null;
        try {
            Class<?> occupancy = Class.forName("io.papermc.paper.entity.poi.PoiType$Occupancy");
            locate = World.class.getMethod("locateAllPoiInRange", Location.class, Predicate.class, int.class, occupancy);
            resultLoc = Class.forName("io.papermc.paper.entity.poi.PoiSearchResult").getMethod("location");
            portalType = Class.forName("io.papermc.paper.entity.poi.PoiTypes").getField("NETHER_PORTAL").get(null);
            any = occupancy.getField("ANY").get(null);
        } catch (ReflectiveOperationException | LinkageError ex) {
            locate = null; // POI API not present on this server
        }
        LOCATE = locate;
        RESULT_LOC = resultLoc;
        NETHER_PORTAL = portalType;
        ANY = any;
    }

    private final Log log;
    private final PortalStore store;

    public PoiSweeper(Log log, PortalStore store) {
        this.log = log;
        this.store = store;
    }

    /** Whether this server has the Paper POI API (Paper 26.1+). */
    public static boolean available() {
        return LOCATE != null;
    }

    /** Sweep for nether portals within {@code radius} of {@code center}; returns the newly stored portals. */
    public List<Portal> sweep(World world, Location center, int radius) {
        return collectAndStore(world, query(world, center, radius));
    }

    /** Query the POIs of a loaded chunk (spanning the full world height). Cheap: POI data is already in memory. */
    public List<Portal> queryChunk(Chunk chunk, int radius) {
        World world = chunk.getWorld();
        Location center = fullHeightCenter(world, chunk.getX() * 16 + 8, chunk.getZ() * 16 + 8);
        return collectAndStore(world, query(world, center, fullHeightRadius(world, radius)));
    }

    /** Full-height column sweep around world coordinates (x, z); the caller's Y is irrelevant on purpose. */
    public List<Portal> sweepColumn(World world, double x, double z, int radius) {
        Location center = fullHeightCenter(world, x, z);
        return collectAndStore(world, query(world, center, fullHeightRadius(world, radius)));
    }

    // Centre the query at the world's vertical midpoint and widen the radius so the sphere spans the full height.
    private static Location fullHeightCenter(World world, double x, double z) {
        return new Location(world, x, (world.getMinHeight() + world.getMaxHeight()) / 2.0, z);
    }

    private static int fullHeightRadius(World world, int radius) {
        return Math.max(radius, (world.getMaxHeight() - world.getMinHeight()) / 2 + 8);
    }

    private List<int[]> query(World world, Location center, int radius) {
        List<int[]> blocks = new ArrayList<>();
        if (LOCATE == null) {
            return blocks;
        }
        try {
            Predicate<Object> isPortal = NETHER_PORTAL::equals;
            List<?> results = (List<?>) LOCATE.invoke(world, center, isPortal, radius, ANY);
            if (results != null) {
                for (Object result : results) {
                    Location loc = (Location) RESULT_LOC.invoke(result);
                    blocks.add(new int[] { loc.getBlockX(), loc.getBlockY(), loc.getBlockZ() });
                }
            }
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        return blocks;
    }

    /** Turn POI block positions into clustered portals and store any new ones. */
    private List<Portal> collectAndStore(World world, List<int[]> blocks) {
        List<Portal> added = new ArrayList<>();
        for (PortalClustering.Cluster cluster : PortalClustering.cluster(blocks)) {
            Portal portal = new Portal(
                    world.getUID(), world.getName(),
                    cluster.centroid()[0], cluster.centroid()[1], cluster.centroid()[2],
                    cluster.min()[0], cluster.min()[1], cluster.min()[2],
                    cluster.max()[0], cluster.max()[1], cluster.max()[2]);
            if (store.add(portal)) {
                added.add(portal);
            }
        }
        if (!added.isEmpty()) {
            log.debug("POI sweep discovered " + added.size() + " new portal(s) in " + world.getName());
        }
        return added;
    }
}
