/*
 * Ported into Bluemarks from BlueMapPortalMarkers (BlueMapBridge) by Max Aldis
 * (https://github.com/maldis018/BlueMapPortalMarkers), licensed GPL-3.0. Modified for Bluemarks.
 */
package dev.iaonnis.bluemarks.portals;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import dev.iaonnis.bluemarks.Bluemarks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps a toggleable "Nether Portals" marker set on every BlueMap map in sync with the {@link PortalStore}.
 *
 * <p>{@link #rebuild} is a full idempotent rebuild, run whenever BlueMap (re)enables. {@link #addPortal} and
 * {@link #removePortal} are live updates and no-ops while BlueMap isn't running (the next rebuild resyncs).
 * Markers use the same icon pipeline as sign markers: the icon is looked up in {@code bluemap/web/markers},
 * anchored at 0,0 and given the {@code bluemarks} style class so the webapp centres, sizes and zoom-scales it.</p>
 */
public final class PortalBridge {

    public static final String MARKER_SET_ID = "nether_portals";

    private final PortalStore store;
    private final Log log;
    // Owned by the manager (filled on the world's thread, read here from any thread), so it is a thread-safe map.
    private final Map<String, PortalLinker.Dimension> dimensions;

    // Mutable so "/bluemarks portals reload" can re-apply them live.
    private volatile String markerSetLabel = "Nether Portals";
    private volatile boolean defaultHidden;
    private volatile String iconName = "nether-portal.webp";
    private volatile double minDistance;
    private volatile double maxDistance;
    private volatile String labelTemplate = "";
    private volatile PortalLinker linker;

    public PortalBridge(PortalStore store, Log log, Map<String, PortalLinker.Dimension> dimensions) {
        this.store = store;
        this.log = log;
        this.dimensions = dimensions;
    }

    /** Read appearance + linking settings from the {@code portals} config section. */
    public void configure(ConfigurationSection c) {
        markerSetLabel = c.getString("label", "Nether Portals");
        defaultHidden = c.getBoolean("default-hidden", false);
        iconName = c.getString("icon", "nether-portal.webp");
        minDistance = c.getDouble("min-distance", 0);
        maxDistance = c.getDouble("max-distance", 0);
        labelTemplate = c.getString("label-template", "");
        linker = c.getBoolean("linking.enabled", true) ? new PortalLinker(c.getDouble("linking.search-tolerance", 128.0)) : null;
    }

    /** Full rebuild from the store, no clear() so a concurrent live add can't be wiped. */
    public void rebuild(BlueMapAPI api) {
        Collection<Portal> snapshot = store.all();
        String icon = Bluemarks.resolveIcon(iconName);

        Set<String> liveIds = new HashSet<>();
        for (Portal portal : snapshot) {
            liveIds.add(portal.markerId());
        }
        for (BlueMapMap map : api.getMaps()) {
            getOrCreateMarkerSet(map).getMarkers().keySet().retainAll(liveIds);
        }
        for (Portal portal : snapshot) {
            BlueMapWorld world = resolveWorld(api, portal);
            if (world == null) {
                continue;
            }
            POIMarker marker = buildMarker(api, portal, snapshot, icon);
            for (BlueMapMap map : world.getMaps()) {
                getOrCreateMarkerSet(map).getMarkers().put(portal.markerId(), marker);
            }
        }
        log.debug("BlueMap portal marker rebuild complete (" + snapshot.size() + " portal(s)).");
    }

    /** Rebuild on the running BlueMap, if any, and refresh the layer's label/visibility (used by reload). */
    public void refresh() {
        Optional<BlueMapAPI> instance = BlueMapAPI.getInstance();
        if (instance.isEmpty()) {
            return;
        }
        BlueMapAPI api = instance.get();
        for (BlueMapMap map : api.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
            if (set != null) {
                set.setLabel(markerSetLabel);
                set.setToggleable(true);
                set.setDefaultHidden(defaultHidden);
            }
        }
        rebuild(api);
    }

    public void addPortal(Portal p) {
        Optional<BlueMapAPI> instance = BlueMapAPI.getInstance();
        if (instance.isEmpty()) {
            return;
        }
        BlueMapAPI api = instance.get();
        BlueMapWorld world = resolveWorld(api, p);
        if (world == null) {
            return;
        }
        POIMarker marker = buildMarker(api, p, store.all(), Bluemarks.resolveIcon(iconName));
        for (BlueMapMap map : world.getMaps()) {
            getOrCreateMarkerSet(map).getMarkers().put(p.markerId(), marker);
        }
    }

    public void removePortal(Portal p) {
        Optional<BlueMapAPI> instance = BlueMapAPI.getInstance();
        if (instance.isEmpty()) {
            return;
        }
        BlueMapAPI api = instance.get();
        BlueMapWorld world = resolveWorld(api, p);
        if (world == null) {
            return;
        }
        for (BlueMapMap map : world.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
            if (set != null) {
                set.getMarkers().remove(p.markerId());
            }
        }
    }

    private MarkerSet getOrCreateMarkerSet(BlueMapMap map) {
        return map.getMarkerSets().computeIfAbsent(MARKER_SET_ID, id -> MarkerSet.builder()
                .label(markerSetLabel)
                .toggleable(true)
                .defaultHidden(defaultHidden)
                .build());
    }

    /** {@code icon} is the already-resolved web path (or null to fall back to BlueMap's default POI icon). */
    private POIMarker buildMarker(BlueMapAPI api, Portal p, Collection<Portal> candidates, String icon) {
        String label = MarkerLabel.format(labelTemplate, p.worldName(), p.x(), p.y(), p.z());
        // Escape user-influenced text (world name, coords) before embedding in HTML.
        String coords = htmlEscape(Math.round(p.x()) + ", " + Math.round(p.y()) + ", " + Math.round(p.z()));
        StringBuilder detail = new StringBuilder()
                .append("<b>").append(htmlEscape(label)).append("</b><br>")
                .append(htmlEscape(p.worldName())).append(" @ ").append(coords);
        appendLinkSection(detail, api, p, candidates);

        POIMarker.Builder b = POIMarker.builder()
                .label(label)
                .position(new Vector3d(p.x(), p.y() + 1, p.z()))
                .detail(detail.toString());
        if (minDistance > 0) {
            b.minDistance(minDistance);
        }
        if (maxDistance > 0) {
            b.maxDistance(maxDistance);
        }
        if (icon != null) {
            // same as sign markers: anchor 0,0 + style class = centred, fixed-size, zoom-scaled by the webapp script
            b.icon(icon, 0, 0).styleClasses(Bluemarks.STYLE_CLASS);
        }
        return b.build();
    }

    /**
     * Appends the predicted Overworld&harr;Nether link to a marker's popup: the predicted counterpart coordinates
     * plus a BlueMap deep-link to the nearest known counterpart. Worded as a prediction, never a guarantee.
     */
    private void appendLinkSection(StringBuilder detail, BlueMapAPI api, Portal p, Collection<Portal> candidates) {
        PortalLinker currentLinker = this.linker;
        if (currentLinker == null) {
            return;
        }
        PortalLinker.Prediction pred = currentLinker.predict(p, candidates, dimensions);
        if (!pred.hasPrediction()) {
            return;
        }
        String predCoords = htmlEscape(Math.round(pred.x()) + ", " + Math.round(pred.y()) + ", " + Math.round(pred.z()));
        detail.append("<br><br><i>Predicted link</i> &rarr; ").append(predCoords);

        Portal counterpart = pred.counterpart();
        if (counterpart == null) {
            detail.append("<br>(no known portal there yet)");
            return;
        }
        String mapId = firstMapId(api, counterpart);
        if (mapId == null) {
            detail.append("<br>(linked portal known; map not currently loaded)");
            return;
        }
        // BlueMap location hash: map:x:y:z:distance:rotation:angle:tilt:ortho:state
        String href = "#" + mapId + ":" + Math.round(counterpart.x()) + ":" + Math.round(counterpart.y()) + ":"
                + Math.round(counterpart.z()) + ":1000:0:0:0:0:perspective";
        detail.append("<br><a href=\"").append(htmlEscape(href)).append("\">Go to linked portal</a>");
    }

    private String firstMapId(BlueMapAPI api, Portal counterpart) {
        BlueMapWorld world = resolveWorld(api, counterpart);
        if (world == null) {
            return null;
        }
        for (BlueMapMap map : world.getMaps()) {
            return map.getId();
        }
        return null;
    }

    /** Thread-safe: resolves the Bukkit world by UUID when loaded (like the sign markers), else by name. */
    private BlueMapWorld resolveWorld(BlueMapAPI api, Portal p) {
        World bukkitWorld = p.worldId() != null ? Bukkit.getWorld(p.worldId()) : null;
        Object key = bukkitWorld != null ? bukkitWorld : p.worldName();
        return api.getWorld(key).orElse(null);
    }

    private static String htmlEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
