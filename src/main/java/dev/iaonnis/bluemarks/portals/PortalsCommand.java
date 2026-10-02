/*
 * Ported into Bluemarks from BlueMapPortalMarkers (PortalsCommand) by Max Aldis
 * (https://github.com/maldis018/BlueMapPortalMarkers), licensed GPL-3.0. Modified for Bluemarks.
 */
package dev.iaonnis.bluemarks.portals;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /bluemarks portals ...} subcommands (reload, sweep, stats, purge). {@code args} here start AFTER
 * the word "portals". Permission is enforced by {@code plugin.yml} ({@code bluemarks.admin}).
 *
 * <p>Folia: sweeps run on the threads owning the swept locations and report back through a callback, so the
 * completion message may arrive a moment after the command returns.</p>
 */
public final class PortalsCommand {

    private static final String PREFIX = "[Bluemarks] ";
    private static final List<String> SUBCOMMANDS = List.of("reload", "sweep", "stats", "purge");

    private final PortalManager manager;

    public PortalsCommand(PortalManager manager) {
        this.manager = manager;
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "sweep" -> handleSweep(sender, args);
            case "stats" -> handleStats(sender);
            case "purge" -> handlePurge(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage(PREFIX + "Configuration reloaded:");
        for (String line : manager.reload()) {
            sender.sendMessage("  - " + line);
        }
    }

    private void handleStats(CommandSender sender) {
        PortalStore store = manager.store();
        sender.sendMessage(PREFIX + "Tracked portals: " + store.size());
        for (World world : Bukkit.getWorlds()) {
            int count = store.inWorld(world.getUID()).size();
            if (count > 0) {
                sender.sendMessage("  - " + world.getName() + ": " + count);
            }
        }
    }

    private void handlePurge(CommandSender sender, String[] args) {
        List<Portal> removed;
        String scope;
        if (args.length >= 2) {
            World world = Bukkit.getWorld(args[1]);
            if (world == null) {
                sender.sendMessage(PREFIX + "Unknown world: " + args[1]);
                return;
            }
            removed = manager.store().removeWorld(world.getUID());
            scope = "world " + world.getName();
        } else {
            removed = manager.store().clear();
            scope = "all worlds";
        }
        for (Portal portal : removed) {
            manager.bridge().removePortal(portal);
        }
        if (!removed.isEmpty()) {
            manager.requestSave();
        }
        sender.sendMessage(PREFIX + "Purged " + removed.size() + " portal(s) from " + scope + ".");
    }

    /**
     * Forms: {@code sweep [radius]} | {@code sweep me [radius]} | {@code sweep <player> [radius]} |
     * {@code sweep <x> [y] <z>} (full-height column in the sender's world, y ignored).
     */
    private void handleSweep(CommandSender sender, String[] args) {
        if (!PoiSweeper.available()) {
            sender.sendMessage(PREFIX + "Sweeps need the Paper POI API (Paper 26.1+). "
                    + "On this server only portals lit or broken while it runs are tracked.");
            return;
        }
        int radius = manager.sweepRadius();
        if (args.length == 1) {
            sweepDefault(sender, radius);
            return;
        }
        String first = args[1];

        if (first.equalsIgnoreCase("me") || !isNumber(first)) {
            Player target;
            if (first.equalsIgnoreCase("me")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PREFIX + "'me' requires a player. Use a player name or coordinates from console.");
                    return;
                }
                target = player;
            } else {
                target = Bukkit.getPlayerExact(first);
                if (target == null) {
                    sender.sendMessage(PREFIX + "Player not found: " + first);
                    return;
                }
            }
            if (args.length > 2) {
                Integer r = parseInt(args[2]);
                if (r == null || r <= 0) {
                    sender.sendMessage(PREFIX + "Radius must be a positive integer.");
                    return;
                }
                radius = r;
            }
            final int used = radius;
            final String who = target == sender ? "you" : target.getName();
            manager.sweepPlayer(target, used, added -> sender.sendMessage(PREFIX + "Sweep around " + who
                    + " complete: " + added + " new portal(s) (radius " + used + ")."));
            return;
        }

        // numeric forms
        for (int i = 1; i < args.length; i++) {
            if (!isNumber(args[i])) {
                sendUsage(sender);
                return;
            }
        }
        int numeric = args.length - 1;
        if (numeric == 1) {
            Integer r = parseInt(args[1]);
            if (r == null || r <= 0) {
                sender.sendMessage(PREFIX + "Radius must be a positive integer.");
                return;
            }
            sweepDefault(sender, r);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "Coordinate sweeps require a player (the world is the sender's). "
                    + "From console, use a player name or omit the center.");
            return;
        }
        if (numeric > 3) {
            sendUsage(sender);
            return;
        }
        double x = Double.parseDouble(args[1]);
        double z = Double.parseDouble(args[numeric == 2 ? 2 : 3]); // with 3 numbers, args[2] is Y: ignored on purpose
        final int used = radius;
        manager.sweepColumn(player.getWorld(), x, z, used, added -> sender.sendMessage(PREFIX + "Full-height sweep at "
                + Math.round(x) + ", " + Math.round(z) + " complete: " + added + " new portal(s) (radius " + used + ")."));
    }

    private void sweepDefault(CommandSender sender, int radius) {
        manager.sweepDefault(radius, added -> sender.sendMessage(PREFIX + "Default sweep complete: " + added
                + " new portal(s) (radius " + radius + ")."));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + "Usage:");
        sender.sendMessage("  /bluemarks portals reload");
        sender.sendMessage("  /bluemarks portals sweep [radius] | me [radius] | <player> [radius] | <x> [y] <z>");
        sender.sendMessage("  /bluemarks portals stats");
        sender.sendMessage("  /bluemarks portals purge [world]");
    }

    public List<String> complete(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            String sub = args[0].toLowerCase();
            if (sub.equals("purge")) {
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase().startsWith(prefix)) {
                        out.add(world.getName());
                    }
                }
            } else if (sub.equals("sweep")) {
                if ("me".startsWith(prefix)) {
                    out.add("me");
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(prefix)) {
                        out.add(player.getName());
                    }
                }
            }
        }
        return out;
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
