package dev.chatcop.listener;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.Scheduler;
import dev.chatcop.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChatListener implements Listener {

    private final ChatCop plugin;
    private volatile Set<String> disabledWorlds = Set.of();

    public ChatListener(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    /** Cached so the hot path doesn't allocate a list for every message. */
    public final void load() {
        List<String> configured = plugin.getConfig().getStringList("general.disabled-worlds");
        Set<String> worlds = new HashSet<>();
        for (String w : configured) {
            if (w != null && !w.isBlank()) worlds.add(w.toLowerCase());
        }
        this.disabledWorlds = worlds;
    }

    public void reload() { load(); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // ── MUTE ─────────────────────────────────────────────────────────────
        // Checked first, deliberately. This used to sit behind the bypass
        // permission and the disabled-world check, so any staff member with
        // chatcop.bypass could talk straight through their own mute, and a
        // muted player only had to walk into a disabled world to chat freely.
        if (handleMuted(player, event)) return;

        // ── CHAT LOCK / JOIN DELAY / SLOWMODE ─────────────────────────────────
        var control = plugin.getChatControlManager();

        if (control.isChatLocked() && !player.hasPermission("chatcop.chatlock.bypass")) {
            event.setCancelled(true);
            send(player, plugin.getConfigManager().getMessage("chat-locked"));
            return;
        }

        long joinWait = control.getJoinDelayRemaining(player);
        if (joinWait > 0) {
            event.setCancelled(true);
            send(player, plugin.getConfigManager().getMessage("join-delay", "{time}", joinWait + "s"));
            return;
        }

        long slowWait = control.consumeSlowmode(player);
        if (slowWait > 0) {
            event.setCancelled(true);
            send(player, plugin.getConfigManager().getMessage("slowmode", "{time}", slowWait + "s"));
            return;
        }

        // ── FILTER BYPASS / DISABLED WORLDS ──────────────────────────────────
        if (player.hasPermission("chatcop.bypass")) return;
        if (disabledWorlds.contains(player.getWorld().getName().toLowerCase())) return;

        // ── FILTER PIPELINE ──────────────────────────────────────────────────
        String message = event.getMessage();
        plugin.getStatsManager().recordMessage();

        FilterResult result = plugin.getFilterManager().process(player, message);

        // Promote a BLOCK to SHADOW when this filter has shadow-mode enabled.
        if (result.getAction() == FilterResult.Action.BLOCK && isShadowFilter(result.getFilterName())) {
            result = FilterResult.shadow(result.getFilterName(), result.getReason(), result.getPoints());
        }

        switch (result.getAction()) {
            case BLOCK -> {
                event.setCancelled(true);
                plugin.getStatsManager().recordBlock();

                if (!plugin.getConfigManager().isSilentBlock()) {
                    send(player, plugin.getConfigManager().getMessage("blocked", "{reason}", result.getReason()));
                }
                handleViolation(player, result, message);
            }

            case SHADOW -> {
                // Ghost punishment: only the sender sees their own message.
                // Deliver it to them manually, then cancel the event instead of
                // just clearing recipients — plugins like DiscordSRV skip
                // cancelled chat events, so this also stops the flagged message
                // being relayed to a linked Discord channel.
                sendShadowEcho(player, event);
                event.setCancelled(true);
                plugin.getStatsManager().recordBlock();
                handleViolation(player, result, message);
            }

            case CENSOR -> {
                plugin.getStatsManager().recordCensor();
                event.setMessage(result.getCensored());
                send(player, plugin.getConfigManager().getMessage("censored"));

                PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
                data.addViolation("[" + result.getFilterName() + " CENSORED] " + message);
                plugin.getPunishmentManager().applyPoints(
                        player, data, result.getPoints(),
                        result.getFilterName(), message, result.getReason());

                plugin.getNotificationManager().alertStaff(
                        player, result.getFilterName() + " [CENSORED]", message, result.getReason());
                plugin.getDiscordManager().sendAlert(
                        player, result.getFilterName(), message, result.getReason(), "CONSOLE");
                plugin.getFileLogger().log(player.getName(), result.getFilterName() + " [CENSORED]", message);
            }

            case ALLOW -> { }
        }
    }

    /** @return true if the player is muted and the event was handled. */
    private boolean handleMuted(Player player, AsyncPlayerChatEvent event) {
        MuteEntry mute = plugin.getMuteManager().getMute(player.getUniqueId());
        if (mute == null) return false;

        event.setCancelled(true);

        String msg = mute.isPermanent()
                ? plugin.getConfigManager().getMessage("muted-permanent",
                        "{reason}", mute.getReason())
                : plugin.getConfigManager().getMessage("muted",
                        "{time}", mute.getRemainingTime(),
                        "{reason}", mute.getReason());
        send(player, msg);
        return true;
    }

    /**
     * Echoes the message back to its sender using the event's own format.
     * Guarded: the format comes from whichever chat plugin is installed, and a
     * stray '%' in it would otherwise throw on the chat thread.
     */
    private void sendShadowEcho(Player player, AsyncPlayerChatEvent event) {
        try {
            player.sendMessage(String.format(event.getFormat(), player.getDisplayName(), event.getMessage()));
        } catch (Exception e) {
            player.sendMessage("<" + player.getDisplayName() + "> " + event.getMessage());
        }
    }

    private void handleViolation(Player player, FilterResult result, String message) {
        PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
        String tag = result.getAction() == FilterResult.Action.SHADOW ? " [SHADOW]" : "";
        data.addViolation("[" + result.getFilterName() + tag + "] " + message);

        plugin.getPunishmentManager().applyPoints(
                player, data, result.getPoints(),
                result.getFilterName(), message, result.getReason());

        plugin.getNotificationManager().alertStaff(player, result.getFilterName() + tag, message, result.getReason());
        plugin.getDiscordManager().sendAlert(player, result.getFilterName(), message, result.getReason(), "CONSOLE");
        plugin.getFileLogger().log(player.getName(), result.getFilterName() + tag, message);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Debug] " + result.getAction() + " "
                    + player.getName() + " (" + result.getFilterName() + "): " + message);
        }
    }

    private boolean isShadowFilter(String filterName) {
        return plugin.getConfig().getBoolean(
                "filters." + filterName.toLowerCase() + ".shadow-mode", false);
    }

    private void send(Player player, String message) {
        player.sendMessage(plugin.getConfigManager().getPrefix() + message);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getChatControlManager().trackJoin(p);
        plugin.getMuteManager().cacheName(p.getName(), p.getUniqueId());

        // Warm the cache so stored points and history are ready before they chat.
        plugin.getFilterManager().getOrCreate(p.getUniqueId());

        UpdateChecker uc = plugin.getUpdateChecker();
        if (uc == null || !uc.isUpdateAvailable()) return;
        if (!plugin.getConfig().getBoolean("update-checker.notify-admins", true)) return;
        if (!p.hasPermission("chatcop.admin")) return;

        // Slight delay so the notice isn't buried under join messages.
        Scheduler.globalLater(plugin, () -> {
            if (!p.isOnline()) return;
            String prefix = plugin.getConfigManager().getPrefix();
            p.sendMessage(prefix + ConfigManager.color("&eA new version &6v" + uc.getLatestVersion()
                    + " &eis available! &7(running v" + plugin.getDescription().getVersion() + ")"));
            p.sendMessage(prefix + ConfigManager.color("&7Download: &b" + uc.getDownloadUrl()));
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getChatControlManager().forget(uuid);

        // Free memory after a grace period (handles quick rejoins). Counters are
        // persisted on the way out, so nothing is lost.
        Scheduler.globalLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                plugin.getFilterManager().removePlayer(uuid);
            }
        }, 20L * 60);
    }
}
