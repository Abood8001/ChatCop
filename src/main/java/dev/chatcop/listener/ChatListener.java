package dev.chatcop.listener;

import dev.chatcop.ChatCop;
import dev.chatcop.config.ConfigManager;
import dev.chatcop.manager.DiscordWebhookManager;
import dev.chatcop.manager.NotificationManager;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.FileLogger;
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

import java.util.List;

public class ChatListener implements Listener {

    private final ChatCop plugin;
    private final NotificationManager notifier;
    private final FileLogger fileLogger;
    private final DiscordWebhookManager discord;

    public ChatListener(ChatCop plugin) {
        this.plugin = plugin;
        this.notifier = new NotificationManager(plugin);
        this.discord = new DiscordWebhookManager(plugin);
        this.fileLogger = new FileLogger(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("chatcop.bypass")) return;

        // Per-world disable: skip filtering in configured worlds
        List<String> disabledWorlds = plugin.getConfig().getStringList("general.disabled-worlds");
        if (disabledWorlds.contains(player.getWorld().getName())) return;

        String message = event.getMessage();

        // ── MUTE CHECK ───────────────────────────────────────────────────────
        if (plugin.getMuteManager().isMuted(player.getUniqueId())) {
            MuteEntry mute = plugin.getMuteManager().getMute(player.getUniqueId());
            event.setCancelled(true);
            if (mute.isPermanent()) {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("muted-permanent"));
            } else {
                player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("muted",
                        "{time}", mute.getRemainingTime()));
            }
            return;
        }

        // ── FILTER PIPELINE ──────────────────────────────────────────────────
        plugin.getStatsManager().recordMessage();
        FilterResult result = plugin.getFilterManager().process(player, message);

        // Promote a BLOCK to SHADOW if this filter has shadow-mode enabled
        if (result.getAction() == FilterResult.Action.BLOCK && isShadowFilter(result.getFilterName())) {
            result = FilterResult.shadow(result.getFilterName(), result.getReason(), result.getPoints());
        }

        switch (result.getAction()) {
            case BLOCK -> {
                event.setCancelled(true);
                plugin.getStatsManager().recordBlock();

                if (!plugin.getConfigManager().isSilentBlock()) {
                    player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("blocked",
                            "{reason}", result.getReason()));
                }
                handleViolation(player, result, message);
            }

            case SHADOW -> {
                // Ghost punishment: only the sender sees their own message.
                // Deliver it to them manually, then cancel the event instead
                // of just clearing recipients — plugins like DiscordSRV skip
                // cancelled chat events by default, so this also stops the
                // flagged message from being relayed to a linked Discord
                // channel. Recipients would have no effect on that relay.
                player.sendMessage(String.format(event.getFormat(), player.getDisplayName(), event.getMessage()));
                event.setCancelled(true);
                plugin.getStatsManager().recordBlock();
                handleViolation(player, result, message);
            }

            case CENSOR -> {
                plugin.getStatsManager().recordCensor();
                event.setMessage(result.getCensored());
                player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("censored"));

                PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
                plugin.getPunishmentManager().applyPoints(
                        player, data, result.getPoints(),
                        result.getFilterName(), message, result.getReason());

                fileLogger.log(player.getName(), result.getFilterName() + " [CENSORED]", message);
            }

            case ALLOW -> { }
        }
    }

    private void handleViolation(Player player, FilterResult result, String message) {
        PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
        String tag = result.getAction() == FilterResult.Action.SHADOW ? " [SHADOW]" : "";
        data.addViolation("[" + result.getFilterName() + tag + "] " + message);

        plugin.getPunishmentManager().applyPoints(
                player, data, result.getPoints(),
                result.getFilterName(), message, result.getReason());

        notifier.alertStaff(player, result.getFilterName() + tag, message, result.getReason());
        discord.sendAlert(player, result.getFilterName(), message, result.getReason(), "CONSOLE");
        fileLogger.log(player.getName(), result.getFilterName() + tag, message);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Debug] " + result.getAction() + " "
                + player.getName() + " (" + result.getFilterName() + "): " + message);
        }
    }

    private boolean isShadowFilter(String filterName) {
        return plugin.getConfig().getBoolean(
                "filters." + filterName.toLowerCase() + ".shadow-mode", false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UpdateChecker uc = plugin.getUpdateChecker();
        if (uc == null || !uc.isUpdateAvailable()) return;
        if (!plugin.getConfig().getBoolean("update-checker.notify-admins", true)) return;

        Player p = event.getPlayer();
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
        // Free memory after a short grace period (handles quick rejoins).
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        Scheduler.globalLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                plugin.getFilterManager().removePlayer(uuid);
            }
        }, 20L * 60); // 60 seconds
    }
}
