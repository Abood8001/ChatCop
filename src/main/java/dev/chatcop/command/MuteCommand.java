package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MuteCommand implements CommandExecutor, TabCompleter {

    private final ChatCop plugin;

    public MuteCommand(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatcop.mute")) {
            sender.sendMessage(prefix() + plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(c("&cUsage: /ccmute <player> [duration] [reason]"));
            return true;
        }

        String playerName = args[0];
        long duration = -1; // permanent unless a duration is given
        String reason = "Muted by staff";

        if (args.length >= 2) {
            long parsed = DurationParser.parse(args[1]);
            if (parsed != 0) {
                duration = parsed;
                if (args.length >= 3) {
                    reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                }
            } else if (looksLikeDuration(args[1])) {
                // Looked like a duration but didn't parse ("0m", "5x", a bare
                // "10"). Don't silently fall through to a permanent mute.
                sender.sendMessage(c("&cInvalid duration: &f" + args[1]
                        + " &7(try 10m, 1h30m, 2d, perm)"));
                return true;
            } else {
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        }

        final long finalDuration = duration;
        final String finalReason = reason;

        // Resolves offline players too, so someone who logged off can still be muted.
        plugin.getMuteManager().resolvePlayer(playerName, uuid -> {
            if (uuid == null) {
                sender.sendMessage(prefix() + plugin.getConfigManager()
                        .getMessage("player-not-found", "{player}", playerName));
                return;
            }
            applyMute(sender, uuid, playerName, finalDuration, finalReason);
        });
        return true;
    }

    private void applyMute(CommandSender sender, UUID uuid, String requestedName,
                           long duration, String reason) {
        Player target = Bukkit.getPlayer(uuid);
        String name = target != null ? target.getName() : requestedName;

        // Staff shouldn't be able to mute each other by default.
        if (target != null && target.hasPermission("chatcop.mute.exempt")
                && !sender.hasPermission("chatcop.mute.override")) {
            sender.sendMessage(prefix() + plugin.getConfigManager()
                    .getMessage("mute-exempt", "{player}", name));
            return;
        }

        if (plugin.getMuteManager().getMute(uuid) != null) {
            sender.sendMessage(prefix() + plugin.getConfigManager()
                    .getMessage("already-muted", "{player}", name));
            return;
        }

        String actor = sender.getName();
        MuteEntry mute = new MuteEntry(uuid, name, reason, actor, duration);
        plugin.getMuteManager().mute(mute);
        plugin.getStatsManager().recordMute();

        String durStr = duration == -1 ? "Permanent" : DurationParser.format(duration);
        sender.sendMessage(prefix() + plugin.getConfigManager().getMessage("mute-success",
                "{player}", name, "{duration}", durStr, "{reason}", reason));

        if (target != null) {
            String msg = duration == -1
                    ? plugin.getConfigManager().getMessage("muted-permanent", "{reason}", reason)
                    : plugin.getConfigManager().getMessage("muted",
                            "{time}", mute.getRemainingTime(), "{reason}", reason);
            target.sendMessage(prefix() + msg);
        }

        plugin.getNotificationManager().alertAction(actor, "muted", name, durStr + " - " + reason);
        plugin.getDiscordManager().sendAction(actor, "Mute", name, durStr, reason);
        plugin.getFileLogger().log(name, "MUTE by " + actor + " (" + durStr + ")", reason);
    }

    /** True if the arg was clearly meant as a duration, so a parse failure is an error rather than a reason. */
    private boolean looksLikeDuration(String arg) {
        return arg.matches("(?i)(perm|permanent|-1|\\d+[a-z].*|\\d+)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chatcop.mute")) return List.of();
        if (args.length == 1) return ChatCopCommand.partial(args[0], ChatCopCommand.onlineNames());
        if (args.length == 2) return ChatCopCommand.partial(args[1],
                List.of("10m", "30m", "1h", "6h", "1d", "7d", "perm"));
        return List.of();
    }

    private String prefix() { return plugin.getConfigManager().getPrefix(); }

    private String c(String s) { return ConfigManager.color(s); }
}
