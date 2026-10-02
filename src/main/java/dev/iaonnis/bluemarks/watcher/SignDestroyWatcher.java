package dev.iaonnis.bluemarks.watcher;


import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import dev.iaonnis.bluemarks.Bluemarks;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Folia note: BlockBreakEvent fires on the thread owning the block's region, where reading the block state is
 * allowed; the marker set itself is thread-safe.
 */
public class SignDestroyWatcher implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onSignDestroy(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Sign)) return;

        MarkerSet set = Bluemarks.markerSet.get(block.getWorld());
        if (set == null) return;

        Vector3d pos = new Vector3d(block.getX(), block.getY(), block.getZ());
        String id = "marker-" + pos.getX() + "-" + pos.getY() + "-" + pos.getZ();

        Marker marker = set.get(id);
        if (marker == null) return;
        set.remove(id);
    }
}
