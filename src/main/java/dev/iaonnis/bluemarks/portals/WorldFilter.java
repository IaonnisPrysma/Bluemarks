/*
 * Ported into Bluemarks from BlueMapPortalMarkers by Max Aldis
 * (https://github.com/maldis018/BlueMapPortalMarkers), licensed GPL-3.0. Modified for Bluemarks.
 */
package dev.iaonnis.bluemarks.portals;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether a world should be included in portal discovery based on a
 * blacklist or whitelist. Pure class — no Bukkit or BlueMap imports.
 */
public final class WorldFilter {

    public enum Mode { BLACKLIST, WHITELIST }

    private final Mode mode;
    private final Set<String> list;

    public WorldFilter(Mode mode, Collection<String> list) {
        this.mode = mode;
        this.list = new HashSet<>(list);
    }

    /** Returns {@code true} if portal discovery should proceed in {@code worldName}. */
    public boolean allows(String worldName) {
        boolean listed = list.contains(worldName);
        return switch (mode) {
            case BLACKLIST -> !listed;
            case WHITELIST -> listed;
        };
    }

    public Mode mode() {
        return mode;
    }
}
