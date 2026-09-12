package dev.chatcop.hook;

import dev.chatcop.ChatCop;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.model.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion.
 *
 * Only loaded when PlaceholderAPI is actually installed — the class is
 * referenced behind a plugin-presence check so its absence never causes a
 * NoClassDefFoundError.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final ChatCop plugin;

    public PlaceholderHook(ChatCop plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "chatcop"; }
    @Override public @NotNull String getAuthor() { return "Abood_8001"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        var stats = plugin.getStatsManager();

        switch (params.toLowerCase()) {
            case "total_messages": return String.valueOf(stats.getTotalMessages());
            case "total_blocked":  return String.valueOf(stats.getBlockedMessages());
            case "total_censored": return String.valueOf(stats.getCensoredMessages());
            case "total_mutes":    return String.valueOf(stats.getTotalMutes());
            case "total_warns":    return String.valueOf(stats.getTotalWarns());
            case "block_rate":     return String.format("%.1f", stats.getBlockRate());
            case "active_mutes":   return String.valueOf(plugin.getMuteManager().getMuteCount());
            case "slowmode":       return String.valueOf(plugin.getChatControlManager().getSlowmodeSeconds());
            case "chat_locked":    return plugin.getChatControlManager().isChatLocked() ? "yes" : "no";
            default: break;
        }

        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "points" -> {
                PlayerData data = plugin.getFilterManager().getIfPresent(player.getUniqueId());
                return String.valueOf(data == null ? 0 : data.getPoints());
            }
            case "warns" -> {
                PlayerData data = plugin.getFilterManager().getIfPresent(player.getUniqueId());
                return String.valueOf(data == null ? 0 : data.getWarnCount());
            }
            case "muted" -> {
                return plugin.getMuteManager().getMute(player.getUniqueId()) != null ? "yes" : "no";
            }
            case "mute_time" -> {
                MuteEntry mute = plugin.getMuteManager().getMute(player.getUniqueId());
                return mute == null ? "" : mute.getRemainingTime();
            }
            case "mute_reason" -> {
                MuteEntry mute = plugin.getMuteManager().getMute(player.getUniqueId());
                return mute == null ? "" : mute.getReason();
            }
            case "bypass" -> {
                Player online = player.getPlayer();
                return (online != null && online.hasPermission("chatcop.bypass")) ? "yes" : "no";
            }
            default -> { return null; }
        }
    }
}
