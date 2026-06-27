package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MuteCommand implements CommandExecutor {

    private final ChatCop plugin;

    public MuteCommand(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatcop.mute")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(c("&cUsage: /ccmute <player> [duration] [reason]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        String playerName = args[0];

        long duration = -1; // permanent by default
        String reason = "Muted by staff";

        if (args.length >= 2) {
            long parsed = DurationParser.parse(args[1]);
            if (parsed != 0) {
                // Recognized as a duration (perm == -1, otherwise a positive length)
                duration = parsed;
                if (args.length >= 3) reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            } else if (looksLikeDuration(args[1])) {
                // Looked like a duration but didn't parse (e.g. "0m", "5x") — don't
                // silently fall through to a permanent mute.
                sender.sendMessage(c("&cInvalid duration: &f" + args[1] + " &7(try 10m, 1h30m, 2d, perm)"));
                return true;
            } else {
                // Second arg isn't a duration, treat the rest as the reason
                reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            }
        }

        java.util.UUID uuid = target != null ? target.getUniqueId() : null;
        if (uuid == null) {
            // Offline player - try to get UUID from server cache
            sender.sendMessage(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("player-not-found", "{player}", playerName));
            return true;
        }

        MuteEntry mute = new MuteEntry(uuid, playerName, reason, duration);
        plugin.getMuteManager().mute(mute);
        plugin.getStatsManager().recordMute();

        String durStr = duration == -1 ? "Permanent" : DurationParser.format(duration);
        sender.sendMessage(plugin.getConfigManager().getPrefix()
            + plugin.getConfigManager().getMessage("mute-success",
                "{player}", playerName, "{duration}", durStr));

        if (target != null) {
            String msg = duration == -1
                ? plugin.getConfigManager().getMessage("muted-permanent")
                : plugin.getConfigManager().getMessage("muted", "{time}", mute.getRemainingTime());
            target.sendMessage(plugin.getConfigManager().getPrefix() + msg);
        }
        return true;
    }

    /** True if the arg was clearly meant as a duration (so a parse failure is an error, not a reason). */
    private boolean looksLikeDuration(String arg) {
        return arg.matches("(?i)(perm|permanent|-1|\\d+[a-z].*)");
    }

    private String c(String s) { return ConfigManager.color(s); }
}
