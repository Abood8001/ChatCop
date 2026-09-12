package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class WarnCommand implements CommandExecutor, TabCompleter {

    private final ChatCop plugin;

    public WarnCommand(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatcop.warn")) {
            sender.sendMessage(prefix() + plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ConfigManager.color("&cUsage: /ccwarn <player> [reason]"));
            return true;
        }

        String name = args[0];
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Inappropriate behavior";

        plugin.getMuteManager().resolvePlayer(name, uuid -> {
            if (uuid == null) {
                sender.sendMessage(prefix() + plugin.getConfigManager()
                        .getMessage("player-not-found", "{player}", name));
                return;
            }

            Player target = Bukkit.getPlayer(uuid);
            PlayerData data = plugin.getFilterManager().getOrCreate(uuid);
            String actor = sender.getName();

            if (target != null) {
                plugin.getPunishmentManager().warn(sender, target, data, reason);
            } else {
                // Offline: record it so the count and history survive, and say so.
                data.incrementWarnCount();
                data.addViolation("WARN: " + reason);
                plugin.getPlayerDataStore().stage(uuid, data);
                plugin.getNotificationManager().alertAction(actor, "warned (offline)", name, reason);
                plugin.getDiscordManager().sendAction(actor, "Warn (offline)", name, null, reason);
                plugin.getFileLogger().log(name, "WARN by " + actor + " (offline)", reason);
            }

            plugin.getStatsManager().recordWarn();
            sender.sendMessage(prefix() + plugin.getConfigManager().getMessage("warn-success",
                    "{player}", target != null ? target.getName() : name,
                    "{reason}", reason,
                    "{count}", String.valueOf(data.getWarnCount())));
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chatcop.warn")) return List.of();
        if (args.length == 1) return ChatCopCommand.partial(args[0], ChatCopCommand.onlineNames());
        return List.of();
    }

    private String prefix() { return plugin.getConfigManager().getPrefix(); }
}
