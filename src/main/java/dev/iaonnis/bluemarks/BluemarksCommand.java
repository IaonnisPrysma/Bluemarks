package dev.iaonnis.bluemarks;

import dev.iaonnis.bluemarks.portals.PortalsCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;

/** {@code /bluemarks}: a single entry point; today it only has {@code portals ...}. */
public final class BluemarksCommand implements CommandExecutor, TabCompleter {

    private final PortalsCommand portals; // null when the portal feature is disabled

    public BluemarksCommand(PortalsCommand portals) {
        this.portals = portals;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (portals != null && args.length >= 1 && args[0].equalsIgnoreCase("portals")) {
            portals.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        } else if (portals == null && args.length >= 1 && args[0].equalsIgnoreCase("portals")) {
            sender.sendMessage("[Bluemarks] The portal markers are disabled (portals.enabled: false in config.yml).");
        } else {
            sender.sendMessage("[Bluemarks] Usage: /bluemarks portals <reload|sweep|stats|purge>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return "portals".startsWith(args[0].toLowerCase()) ? List.of("portals") : List.of();
        }
        if (portals != null && args.length > 1 && args[0].equalsIgnoreCase("portals")) {
            return portals.complete(Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }
}
