package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnmuteCommand implements CommandExecutor {

    private final ChatCop plugin;

    public UnmuteCommand(ChatCop plugin) {
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
            sender.sendMessage(ConfigManager.color("&cUsage: /ccunmute <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("player-not-found", "{player}", args[0]));
            return true;
        }

        plugin.getMuteManager().unmute(target.getUniqueId());
        sender.sendMessage(plugin.getConfigManager().getPrefix()
            + plugin.getConfigManager().getMessage("unmute-success", "{player}", target.getName()));
        return true;
    }
}
