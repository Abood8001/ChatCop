package dev.chatcop.listener;

import dev.chatcop.ChatCop;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies the chat filters to chat-like commands.
 *
 * Without this, every filter is one "/msg" away from being bypassed: private
 * messages, /me and /mail were never inspected, and a muted player could keep
 * talking through them.
 */
public class CommandListener implements Listener {

    private final ChatCop plugin;

    /** command name -> how many arguments precede the message itself. */
    private volatile Map<String, Integer> filtered = Map.of();
    private volatile Set<String> blockedWhenMuted = Set.of();
    private volatile boolean enabled = true;

    public CommandListener(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    public final void load() {
        enabled = plugin.getConfig().getBoolean("command-filter.enabled", true);

        Map<String, Integer> parsed = new HashMap<>();
        for (String entry : plugin.getConfig().getStringList("command-filter.filtered")) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.trim().split(":");
            String name = parts[0].trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            int skip = 0;
            if (parts.length > 1) {
                try { skip = Math.max(0, Integer.parseInt(parts[1].trim())); }
                catch (NumberFormatException ignored) { /* default 0 */ }
            }
            parsed.put(name, skip);
        }
        this.filtered = Map.copyOf(parsed);

        Set<String> muted = new HashSet<>();
        for (String entry : plugin.getConfig().getStringList("command-filter.blocked-when-muted")) {
            if (entry != null && !entry.isBlank()) muted.add(entry.trim().toLowerCase(Locale.ROOT));
        }
        this.blockedWhenMuted = Set.copyOf(muted);
    }

    public void reload() { load(); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (raw.length() < 2 || raw.charAt(0) != '/') return;

        String[] parts = raw.substring(1).split(" ");
        if (parts.length == 0) return;

        String label = stripNamespace(parts[0].toLowerCase(Locale.ROOT));

        // Muted players can't route around the mute through /msg and friends.
        if (blockedWhenMuted.contains(label)) {
            MuteEntry mute = plugin.getMuteManager().getMute(player.getUniqueId());
            if (mute != null) {
                event.setCancelled(true);
                String msg = mute.isPermanent()
                        ? plugin.getConfigManager().getMessage("muted-permanent", "{reason}", mute.getReason())
                        : plugin.getConfigManager().getMessage("muted",
                                "{time}", mute.getRemainingTime(), "{reason}", mute.getReason());
                player.sendMessage(plugin.getConfigManager().getPrefix() + msg);
                return;
            }
        }

        Integer skip = filtered.get(label);
        if (skip == null) return;

        if (player.hasPermission("chatcop.bypass")) return;

        // Everything after the command and its leading arguments is the message.
        int firstWord = 1 + skip;
        if (parts.length <= firstWord) return;

        StringBuilder sb = new StringBuilder();
        for (int i = firstWord; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        String message = sb.toString();
        if (message.isBlank()) return;

        plugin.getStatsManager().recordMessage();
        FilterResult result = plugin.getFilterManager().process(player, message);

        if (result.getAction() == FilterResult.Action.BLOCK
                || result.getAction() == FilterResult.Action.SHADOW) {
            event.setCancelled(true);
            plugin.getStatsManager().recordBlock();

            if (!plugin.getConfigManager().isSilentBlock()) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("blocked", "{reason}", result.getReason()));
            }
            report(player, result, message, "/" + label);

        } else if (result.getAction() == FilterResult.Action.CENSOR) {
            plugin.getStatsManager().recordCensor();

            StringBuilder rebuilt = new StringBuilder("/");
            for (int i = 0; i < firstWord; i++) {
                rebuilt.append(parts[i]).append(' ');
            }
            rebuilt.append(result.getCensored());
            event.setMessage(rebuilt.toString());

            player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("censored"));
            report(player, result, message, "/" + label + " [CENSORED]");
        }
    }

    private void report(Player player, FilterResult result, String message, String context) {
        PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
        data.addViolation("[" + result.getFilterName() + " " + context + "] " + message);

        plugin.getPunishmentManager().applyPoints(
                player, data, result.getPoints(),
                result.getFilterName(), message, result.getReason());

        plugin.getNotificationManager().alertStaff(
                player, result.getFilterName() + " " + context, message, result.getReason());
        plugin.getDiscordManager().sendAlert(
                player, result.getFilterName(), context + " " + message, result.getReason(), "CONSOLE");
        plugin.getFileLogger().log(player.getName(), result.getFilterName() + " " + context, message);
    }

    /** "essentials:msg" -> "msg" */
    private String stripNamespace(String label) {
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }
}
