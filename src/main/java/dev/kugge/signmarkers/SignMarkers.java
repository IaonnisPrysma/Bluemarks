package dev.kugge.signmarkers;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import dev.kugge.signmarkers.watcher.SignDestroyWatcher;
import dev.kugge.signmarkers.watcher.SignWatcher;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SignMarkers extends JavaPlugin {

    public static Path webRoot;
    public static SignMarkers instance;
    public static Logger logger;
    public static Map<World, MarkerSet> markerSet = new ConcurrentHashMap<>();

    /** CSS class put on every sign marker so the webapp script/style can find it. */
    public static final String STYLE_CLASS = "signmarker";
    /** Supported icon formats, tried in this order when the sign has no extension. */
    public static final List<String> ICON_EXTENSIONS = List.of(".png", ".webp", ".gif", ".jpg", ".jpeg", ".svg");
    private static final String WEB_DIR = "signmarkers";

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        saveDefaultConfig();
        createFiles();
        for (World world : Bukkit.getWorlds()) {
            loadWorldMarkerSet(world);
            registerWorld(world);
        }
        BlueMapAPI.onEnable(api -> {
            webRoot = api.getWebApp().getWebRoot();
            installWebAssets(api);
        });
        Bukkit.getPluginManager().registerEvents(new SignWatcher(), this);
        Bukkit.getPluginManager().registerEvents(new SignDestroyWatcher(), this);
    }

    @Override
    public void onDisable() {
        for (World world : Bukkit.getWorlds()) saveWorldMarkerSet(world);
    }

    /**
     * Resolves a sign's icon name to a web path like "./markers/name.webp", or null if no such file exists.
     * The name may include an extension; otherwise the supported formats are tried in order.
     */
    public static String resolveIcon(String name) {
        if (webRoot == null || name == null) return null;
        name = name.trim();
        if (name.isEmpty()) return null;

        Path markersDir = webRoot.resolve("markers").normalize();
        String lower = name.toLowerCase();
        boolean hasExtension = ICON_EXTENSIONS.stream().anyMatch(lower::endsWith);

        for (String ext : hasExtension ? List.of("") : ICON_EXTENSIONS) {
            Path file;
            try {
                file = markersDir.resolve(name + ext).normalize();
            } catch (java.nio.file.InvalidPathException ex) {
                return null;
            }
            // players type this text, so never allow escaping the markers directory
            if (!file.startsWith(markersDir)) return null;
            if (Files.isRegularFile(file)) return "./markers/" + name + ext;
        }
        return null;
    }

    /** Writes the webapp script + style (with values from config.yml) into the BlueMap web root and registers them. */
    private void installWebAssets(BlueMapAPI api) {
        try {
            Path dir = webRoot.resolve(WEB_DIR);
            Files.createDirectories(dir);

            String js = readResource("web/signmarkers.js")
                .replace("%%ICON_SIZE%%", String.valueOf(getConfig().getDouble("icon-size", 32)))
                .replace("%%SCALING_ENABLED%%", String.valueOf(getConfig().getBoolean("scaling.enabled", true)))
                .replace("%%REF_DISTANCE%%", String.valueOf(getConfig().getDouble("scaling.reference-distance", 150)))
                .replace("%%EXPONENT%%", String.valueOf(getConfig().getDouble("scaling.exponent", 0.5)))
                .replace("%%MIN_SCALE%%", String.valueOf(getConfig().getDouble("scaling.min-scale", 0.3)))
                .replace("%%MAX_SCALE%%", String.valueOf(getConfig().getDouble("scaling.max-scale", 1.5)))
                .replace("%%DEBUG%%", String.valueOf(getConfig().getBoolean("debug", false)));
            Files.writeString(dir.resolve("signmarkers.js"), js, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("signmarkers.css"), readResource("web/signmarkers.css"), StandardCharsets.UTF_8);

            api.getWebApp().registerScript(WEB_DIR + "/signmarkers.js");
            api.getWebApp().registerStyle(WEB_DIR + "/signmarkers.css");
        } catch (NoSuchMethodError ex) {
            logger.warning("This BlueMap version cannot register webapp scripts. Add \"" + WEB_DIR + "/signmarkers.js\" to "
                + "'scripts' and \"" + WEB_DIR + "/signmarkers.css\" to 'styles' in BlueMap's webapp.conf to enable icon scaling.");
        } catch (IOException ex) {
            logger.severe("Could not install the webapp scripts, icons will not scale: " + ex.getMessage());
        }
    }

    private String readResource(String path) throws IOException {
        try (InputStream in = getResource(path)) {
            if (in == null) throw new FileNotFoundException(path + " is missing from the plugin jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Markers saved by older versions have a pixel anchor and no style class; convert them to the centered style. */
    private void migrateMarkers(MarkerSet set) {
        for (Marker marker : set.getMarkers().values()) {
            if (!(marker instanceof POIMarker poi)) continue;
            String icon = poi.getIconAddress();
            if (icon == null || !icon.startsWith("./markers/")) continue;
            if (poi.getStyleClasses().contains(STYLE_CLASS)) continue;
            poi.setIcon(icon, 0, 0);
            poi.addStyleClasses(List.of(STYLE_CLASS));
        }
    }

    private void createFiles() {
        for (World world : Bukkit.getWorlds()) {
            String name = "marker-set-" + world.getName() + ".json";
            File file = new File(this.getDataFolder(), name);
            try {
                File folder = this.getDataFolder();
                if (!folder.exists()) folder.mkdirs();
                if (!file.exists()) file.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void saveWorldMarkerSet(World world) {
        String name = "marker-set-" + world.getName() + ".json";
        File file = new File(this.getDataFolder(), name);
        try (FileWriter writer = new FileWriter(file)) {
            MarkerGson.INSTANCE.toJson(markerSet.get(world), writer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadWorldMarkerSet(World world) {
        String name = "marker-set-" + world.getName() + ".json";
        File file = new File(this.getDataFolder(), name);
        try (FileReader reader = new FileReader(file)) {
            MarkerSet set = MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class);
            if (set != null) {
                migrateMarkers(set);
                markerSet.put(world, set);
            }
        } catch (FileNotFoundException ignored) {
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void registerWorld(World world) {
        BlueMapAPI.onEnable(api ->
            api.getWorld(world).ifPresent(blueWorld -> {
                for (BlueMapMap map : blueWorld.getMaps()) {
                    String label = "sign-markers-" + world.getName();
                    MarkerSet set = markerSet.get(world);
                    if (set == null) set = MarkerSet.builder().label(label).build();
                    map.getMarkerSets().put(label, set);
                    markerSet.put(world, set);
                }
            })
        );
    }
}
