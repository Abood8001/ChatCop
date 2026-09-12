package dev.chatcop.command;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.manager.PlayerDataStore;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatCopCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "stats", "history", "check", "test",
            "mutelist", "slowmode", "lock", "unlock", "clear");

    private final ChatCop plugin;

    public ChatCopCommand(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!require(sender, "chatcop.admin")) return true;
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!require(sender, "chatcop.admin")) return true;
                plugin.reload();
                sender.sendMessage(prefix() + plugin.getConfigManager().getMessage("reload-success"));
            }

            // Honours chatcop.stats, which was declared in plugin.yml and
            // documented but never actually checked.
            case "stats" -> {
                if (!sender.hasPermission("chatcop.stats") && !require(sender, "chatcop.admin")) return true;
                sendStats(sender);
            }

            case "history" -> {
                if (!require(sender, "chatcop.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage(c("&cUsage: /chatcop history <player>"));
                    return true;
                }
                sendHistory(sender, args[1]);
            }

            case "check" -> {
                if (!require(sender, "chatcop.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage(c("&cUsage: /chatcop check <player>"));
                    return true;
                }
                sendCheck(sender, args[1]);
            }

            case "test" -> {
                if (!require(sender, "chatcop.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage(c("&cUsage: /chatcop test <message>"));
                    return true;
                }
                String testMsg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                // Uses the stateless test path, so testing phrases never counts
                // towards the tester's own flood or duplicate tracking.
                FilterResult result = plugin.getFilterManager().test(testMsg);

                if (result.isClean()) {
                    sender.sendMessage(prefix() + c("&aClean &7- this message would pass."));
                } else {
                    boolean shadow = plugin.getConfig().getBoolean(
                            "filters." + result.getFilterName().toLowerCase(Locale.ROOT) + ".shadow-mode", false);
                    String action = (result.getAction() == FilterResult.Action.BLOCK && shadow)
                            ? "SHADOW" : result.getAction().name();

                    sender.sendMessage(prefix() + c("&cFlagged &8| &fFilter: &e" + result.getFilterName()
                            + " &8| &fAction: &e" + action
                            + " &8| &fPoints: &e" + result.getPoints()
                            + " &8| &fReason: &7" + result.getReason()));
                    if (result.getAction() == FilterResult.Action.CENSOR) {
                        sender.sendMessage(prefix() + c("&7Would send as: &f" + result.getCensored()));
                    }
                }
            }

            case "mutelist" -> {
                if (!require(sender, "chatcop.mute")) return true;
                sendMuteList(sender);
            }

            case "slowmode" -> {
                if (!require(sender, "chatcop.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage(prefix() + c("&7Slowmode is currently &f"
                            + plugin.getChatControlManager().getSlowmodeSeconds() + "s&7."));
                    return true;
                }
                try {
                    long seconds = Long.parseLong(args[1]);
                    if (seconds < 0) throw new NumberFormatException();
                    plugin.getChatControlManager().setSlowmodeSeconds(seconds);
                    sender.sendMessage(prefix() + (seconds == 0
                            ? c("&aSlowmode disabled.")
                            : c("&aSlowmode set to &f" + seconds + "s&a.")));
                } catch (NumberFormatException e) {
                    sender.sendMessage(c("&cUsage: /chatcop slowmode <seconds>  &7(0 to disable)"));
                }
            }

            case "lock" -> {
                if (!require(sender, "chatcop.admin")) return true;
                plugin.getChatControlManager().setChatLocked(true);
                Bukkit.broadcastMessage(prefix() + plugin.getConfigManager().getMessage("chat-lock-on"));
            }

            case "unlock" -> {
                if (!require(sender, "chatcop.admin")) return true;
                plugin.getChatControlManager().setChatLocked(false);
                Bukkit.broadcastMessage(prefix() + plugin.getConfigManager().getMessage("chat-lock-off"));
            }

            case "clear" -> {
                if (!require(sender, "chatcop.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage(c("&cUsage: /chatcop clear <player>"));
                    return true;
                }
                clearPlayer(sender, args[1]);
            }

            case "help" -> {
                if (!require(sender, "chatcop.admin")) return true;
                sendHelp(sender);
            }

            default -> {
                if (!require(sender, "chatcop.admin")) return true;
                sendHelp(sender);
            }
        }
        return true;
    }

    private void sendStats(CommandSender sender) {
        var stats = plugin.getStatsManager();
        sender.sendMessage(c("&8&m                              "));
        sender.sendMessage(prefix() + c("&b&lChatCop Statistics"));
        sender.sendMessage(c("&7Total Messages:  &f" + stats.getTotalMessages()));
        sender.sendMessage(c("&7Blocked:         &c" + stats.getBlockedMessages()
                + " &8(&c" + String.format("%.1f", stats.getBlockRate()) + "%&8)"));
        sender.sendMessage(c("&7Censored:        &e" + stats.getCensoredMessages()));
        sender.sendMessage(c("&7Total Mutes:     &6" + stats.getTotalMutes()));
        sender.sendMessage(c("&7Total Warns:     &6" + stats.getTotalWarns()));
        sender.sendMessage(c("&7Active Mutes:    &6" + plugin.getMuteManager().getMuteCount()));
        sender.sendMessage(c("&7Tracked Players: &a" + plugin.getFilterManager().getTrackedPlayerCount()));
        sender.sendMessage(c("&7Chat Locked:     &f" + (plugin.getChatControlManager().isChatLocked() ? "yes" : "no")));
        sender.sendMessage(c("&7Slowmode:        &f" + plugin.getChatControlManager().getSlowmodeSeconds() + "s"));
        sender.sendMessage(c("&8&m                              "));
    }

    /** Works for offline players too, by reading persisted counters. */
    private void sendHistory(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerData data = plugin.getFilterManager().getOrCreate(online.getUniqueId());
            printHistory(sender, online.getName(), data.getPoints(), data.getWarnCount(), data.getViolationHistory());
            return;
        }

        plugin.getMuteManager().resolvePlayer(name, uuid -> {
            if (uuid == null) {
                sender.sendMessage(prefix() + plugin.getConfigManager()
                        .getMessage("player-not-found", "{player}", name));
                return;
            }
            PlayerDataStore.StoredSnapshot snap = plugin.getPlayerDataStore().peek(uuid);
            if (snap == null) {
                printHistory(sender, name, 0, 0, List.of());
            } else {
                printHistory(sender, name, snap.points(), snap.warns(), snap.history());
            }
        });
    }

    private void printHistory(CommandSender sender, String name, int points, int warns, List<String> history) {
        sender.sendMessage(prefix() + c("&bViolation history for &f" + name
                + " &8(points: &c" + points + "&8, warns: &e" + warns + "&8)"));
        if (history.isEmpty()) {
            sender.sendMessage(c("  &7No violations recorded."));
            return;
        }
        for (int i = 0; i < Math.min(history.size(), 10); i++) {
            sender.sendMessage(c("  &8" + (i + 1) + ". &7" + history.get(i)));
        }
    }

    private void sendCheck(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            sender.sendMessage(prefix() + plugin.getConfigManager()
                    .getMessage("player-not-found", "{player}", name));
            return;
        }
        PlayerData data = plugin.getFilterManager().getOrCreate(online.getUniqueId());
        MuteEntry mute = plugin.getMuteManager().getMute(online.getUniqueId());

        sender.sendMessage(prefix() + c("&bStatus for &f" + online.getName()));
        sender.sendMessage(c("  &7Points:  &c" + data.getPoints()));
        sender.sendMessage(c("  &7Warns:   &e" + data.getWarnCount()));
        sender.sendMessage(c("  &7Muted:   &f" + (mute == null ? "no"
                : mute.getRemainingTime() + " &8(&7" + mute.getReason() + "&8)")));
        sender.sendMessage(c("  &7Bypass:  &f" + (online.hasPermission("chatcop.bypass") ? "yes" : "no")));
    }

    private void sendMuteList(CommandSender sender) {
        List<MuteEntry> active = plugin.getMuteManager().getActiveMutes();
        sender.sendMessage(prefix() + c("&bActive mutes &8(&f" + active.size() + "&8)"));
        if (active.isEmpty()) {
            sender.sendMessage(c("  &7Nobody is muted."));
            return;
        }
        for (int i = 0; i < Math.min(active.size(), 20); i++) {
            MuteEntry m = active.get(i);
            sender.sendMessage(c("  &8- &f" + m.getPlayerName() + " &8| &7" + m.getRemainingTime()
                    + " &8| &7" + m.getReason() + " &8(by " + m.getMutedBy() + ")"));
        }
        if (active.size() > 20) {
            sender.sendMessage(c("  &8... and " + (active.size() - 20) + " more."));
        }
    }

    private void clearPlayer(CommandSender sender, String name) {
        plugin.getMuteManager().resolvePlayer(name, uuid -> {
            if (uuid == null) {
                sender.sendMessage(prefix() + plugin.getConfigManager()
                        .getMessage("player-not-found", "{player}", name));
                return;
            }
            PlayerData data = plugin.getFilterManager().getIfPresent(uuid);
            if (data != null) {
                data.resetPoints();
                data.clearHistory();
            }
            plugin.getPlayerDataStore().clear(uuid);
            sender.sendMessage(prefix() + c("&aCleared violation points and history for &f" + name + "&a."));
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(c("&8&m                              "));
        sender.sendMessage(prefix() + c("&b&lChatCop &7v" + plugin.getDescription().getVersion()));
        sender.sendMessage(c("  &b/chatcop reload &8- &7Reload configuration"));
        sender.sendMessage(c("  &b/chatcop stats &8- &7View statistics"));
        sender.sendMessage(c("  &b/chatcop history <player> &8- &7View violation history"));
        sender.sendMessage(c("  &b/chatcop check <player> &8- &7Points, warns and mute status"));
        sender.sendMessage(c("  &b/chatcop test <message> &8- &7Test what a message would trigger"));
        sender.sendMessage(c("  &b/chatcop clear <player> &8- &7Reset points and history"));
        sender.sendMessage(c("  &b/chatcop mutelist &8- &7List active mutes"));
        sender.sendMessage(c("  &b/chatcop slowmode <seconds> &8- &7Set chat slowmode"));
        sender.sendMessage(c("  &b/chatcop lock &8| &b/chatcop unlock &8- &7Lock or unlock chat"));
        sender.sendMessage(c("  &b/ccmute <player> [duration] [reason]"));
        sender.sendMessage(c("  &b/ccunmute <player>  &8| &b/ccwarn <player> [reason]"));
        sender.sendMessage(c("&8&m                              "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chatcop.admin") && !sender.hasPermission("chatcop.stats")) {
            return List.of();
        }

        if (args.length == 1) {
            return partial(args[0], SUBCOMMANDS);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "history", "check", "clear" -> partial(args[1], onlineNames());
                case "slowmode" -> partial(args[1], List.of("0", "3", "5", "10", "30"));
                default -> List.of();
            };
        }
        return List.of();
    }

    static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    static List<String> partial(String typed, List<String> options) {
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(option);
        }
        return out;
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(prefix() + plugin.getConfigManager().getMessage("no-permission"));
        return false;
    }

    private String prefix() { return plugin.getConfigManager().getPrefix(); }

    private String c(String s) { return ConfigManager.color(s); }
}
