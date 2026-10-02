package dev.iaonnis.bluemarks.watcher;

import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import dev.iaonnis.bluemarks.Bluemarks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import com.flowpowered.math.vector.Vector3d;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Folia note: SignChangeEvent fires on the thread owning the sign's region. This listener only touches the
 * thread-safe BlueMap marker set and the event itself, so it needs no scheduler hops.
 */
public class SignWatcher implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onSignWrite(SignChangeEvent event) {
        Component header = event.line(0);
        if (header == null) return;
        // plain text instead of Component#toString(), whose format differs between Adventure versions
        if (!PlainTextComponentSerializer.plainText().serialize(header).contains("[map]")) return;

        Component cicon = event.line(3);
        if (cicon == Component.empty() || cicon == null) return;

        String icon = Bluemarks.resolveIcon(PlainTextComponentSerializer.plainText().serialize(cicon));
        if (icon == null) return;

        Component clabel1 = event.line(1);
        if (clabel1 == Component.empty() || clabel1 == null) return;

        Component clabel2 = event.line(2);
        String label = LegacyComponentSerializer.legacySection().serialize(clabel1)
                     + LegacyComponentSerializer.legacySection().serialize(clabel2);

        Block block = event.getBlock();
        Vector3d pos = new Vector3d(block.getX(), block.getY(), block.getZ());

        String id = "marker-" + pos.getX() + "-" + pos.getY() + "-" + pos.getZ();
        POIMarker marker = POIMarker.builder()
                .label(label)
                .position(pos)
                // anchor 0,0: the webapp style centers the icon on the marker and scales it with the zoom
                .icon(icon, 0, 0)
                .styleClasses(Bluemarks.STYLE_CLASS)
                .maxDistance(100000)
                .build();
        MarkerSet set = Bluemarks.markerSet.get(block.getWorld());
        if (set == null) return;
        set.put(id, marker);

        // Delete [map] and icon lines
        event.line(0, Component.empty());
        event.line(3, Component.empty());
    }
}
