package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class WarnCommand implements CommandExecutor {

    private final ChatCop plugin;

    public WarnCommand(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatcop.warn")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ConfigManager.color("&cUsage: /ccwarn <player> [reason]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("player-not-found", "{player}", args[0]));
            return true;
        }

        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Inappropriate behavior";
        PlayerData data = plugin.getFilterManager().getOrCreate(target.getUniqueId());
        plugin.getPunishmentManager().warn(target, data, reason);
        plugin.getStatsManager().recordWarn();

        sender.sendMessage(plugin.getConfigManager().getPrefix()
            + plugin.getConfigManager().getMessage("warn-success",
                "{player}", target.getName(), "{reason}", reason));
        return true;
    }
}
