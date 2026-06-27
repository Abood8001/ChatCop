package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ChatCopCommand implements CommandExecutor {

    private final ChatCop plugin;

    public ChatCopCommand(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatcop.admin")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("reload-success"));
            }

            case "stats" -> sendStats(sender);

            case "history" -> {
                if (args.length < 2) { sender.sendMessage(c("&cUsage: /chatcop history <player>")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("player-not-found", "{player}", args[1])); return true; }
                sendHistory(sender, target);
            }

            case "test" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(c("&cOnly players can use /chatcop test."));
                    return true;
                }
                if (args.length < 2) { sender.sendMessage(c("&cUsage: /chatcop test <message>")); return true; }
                String testMsg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                var result = plugin.getFilterManager().process(p, testMsg);
                String p2 = plugin.getConfigManager().getPrefix();
                if (result.isClean()) {
                    sender.sendMessage(p2 + c("&aClean &7- this message would pass."));
                } else {
                    sender.sendMessage(p2 + c("&cFlagged &8| &fFilter: &e" + result.getFilterName()
                        + " &8| &fAction: &e" + result.getAction()
                        + " &8| &fReason: &7" + result.getReason()));
                }
            }

            case "help" -> sendHelp(sender);

            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendStats(CommandSender sender) {
        var stats = plugin.getStatsManager();
        String p = plugin.getConfigManager().getPrefix();
        sender.sendMessage(c("&8&m                              "));
        sender.sendMessage(p + c("&b&lChatCop Statistics"));
        sender.sendMessage(c("&7Total Messages:  &f" + stats.getTotalMessages()));
        sender.sendMessage(c("&7Blocked:         &c" + stats.getBlockedMessages()
            + " &8(&c" + String.format("%.1f", stats.getBlockRate()) + "%&8)"));
        sender.sendMessage(c("&7Censored:        &e" + stats.getCensoredMessages()));
        sender.sendMessage(c("&7Total Mutes:     &6" + stats.getTotalMutes()));
        sender.sendMessage(c("&7Active Mutes:    &6" + plugin.getMuteManager().getMuteCount()));
        sender.sendMessage(c("&7Tracked Players: &a" + plugin.getFilterManager().getTrackedPlayerCount()));
        sender.sendMessage(c("&8&m                              "));
    }

    private void sendHistory(CommandSender sender, Player target) {
        PlayerData data = plugin.getFilterManager().getOrCreate(target.getUniqueId());
        List<String> history = data.getViolationHistory();
        String p = plugin.getConfigManager().getPrefix();
        sender.sendMessage(p + c("&bViolation history for &f" + target.getName()
            + " &8(points: &c" + data.getPoints() + "&8, warns: &e" + data.getWarnCount() + "&8)"));
        if (history.isEmpty()) {
            sender.sendMessage(c("  &7No violations recorded this session."));
        } else {
            for (int i = 0; i < Math.min(history.size(), 10); i++) {
                sender.sendMessage(c("  &8" + (i + 1) + ". &7" + history.get(i)));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        String p = plugin.getConfigManager().getPrefix();
        sender.sendMessage(c("&8&m                              "));
        sender.sendMessage(p + c("&b&lChatCop &7v" + plugin.getDescription().getVersion()));
        sender.sendMessage(c("  &b/chatcop reload     &8- &7Reload configuration"));
        sender.sendMessage(c("  &b/chatcop stats      &8- &7View statistics"));
        sender.sendMessage(c("  &b/chatcop history <player> &8- &7View violation history"));
        sender.sendMessage(c("  &b/chatcop test <message> &8- &7Test what a message would trigger"));
        sender.sendMessage(c("  &b/ccmute <player> [duration] [reason]"));
        sender.sendMessage(c("  &b/ccunmute <player>"));
        sender.sendMessage(c("  &b/ccwarn <player> [reason]"));
        sender.sendMessage(c("&8&m                              "));
    }

    private String c(String s) { return ConfigManager.color(s); }
}
