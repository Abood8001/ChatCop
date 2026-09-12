package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.model.MuteEntry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class UnmuteCommand implements CommandExecutor, TabCompleter {

    private final ChatCop plugin;

    public UnmuteCommand(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatcop.mute")) {
            sender.sendMessage(prefix() + plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ConfigManager.color("&cUsage: /ccunmute <player>"));
            return true;
        }

        String name = args[0];

        // Offline resolution matters most here: a player muted for a week who
        // then logs off previously could not be unmuted at all.
        plugin.getMuteManager().resolvePlayer(name, uuid -> {
            if (uuid == null) {
                sender.sendMessage(prefix() + plugin.getConfigManager()
                        .getMessage("player-not-found", "{player}", name));
                return;
            }

            if (!plugin.getMuteManager().unmute(uuid)) {
                sender.sendMessage(prefix() + plugin.getConfigManager()
                        .getMessage("not-muted", "{player}", name));
                return;
            }

            Player target = Bukkit.getPlayer(uuid);
            String display = target != null ? target.getName() : name;
            String actor = sender.getName();

            sender.sendMessage(prefix() + plugin.getConfigManager()
                    .getMessage("unmute-success", "{player}", display));
            if (target != null) {
                target.sendMessage(prefix() + plugin.getConfigManager().getMessage("unmuted"));
            }

            plugin.getNotificationManager().alertAction(actor, "unmuted", display, "");
            plugin.getDiscordManager().sendAction(actor, "Unmute", display, null, null);
            plugin.getFileLogger().log(display, "UNMUTE by " + actor, "");
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chatcop.mute")) return List.of();
        if (args.length != 1) return List.of();

        // Suggest who is actually muted, rather than everyone online.
        List<String> muted = new ArrayList<>();
        for (MuteEntry m : plugin.getMuteManager().getActiveMutes()) muted.add(m.getPlayerName());
        return ChatCopCommand.partial(args[0], muted);
    }

    private String prefix() { return plugin.getConfigManager().getPrefix(); }
}
