package dev.chatcop.listener;

import dev.chatcop.ChatCop;
import dev.chatcop.manager.DiscordWebhookManager;
import dev.chatcop.manager.NotificationManager;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.FileLogger;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

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

        // Bypass permission
        if (player.hasPermission("chatcop.bypass")) return;

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

        switch (result.getAction()) {
            case BLOCK -> {
                event.setCancelled(true);
                plugin.getStatsManager().recordBlock();

                // Notify player (unless silent mode)
                if (!plugin.getConfigManager().isSilentBlock()) {
                    player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("blocked",
                            "{reason}", result.getReason()));
                }

                // Award points & possibly punish
                PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
                data.addViolation("[" + result.getFilterName() + "] " + message);
                plugin.getPunishmentManager().applyPoints(
                        player, data, result.getPoints(),
                        result.getFilterName(), message, result.getReason()
                );

                // Staff alert
                notifier.alertStaff(player, result.getFilterName(), message, result.getReason());
                discord.sendAlert(player, result.getFilterName(), message, result.getReason(), "CONSOLE");

                // File log
                fileLogger.log(player.getName(), result.getFilterName(), message);

                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[Debug] Blocked " + player.getName()
                        + " (" + result.getFilterName() + "): " + message);
                }
            }

            case CENSOR -> {
                // Replace message component with censored version
                plugin.getStatsManager().recordCensor();
                String censored = result.getCensored();

                // Rewrite the message using Paper API
                event.setMessage(result.getCensored());

                // Optionally notify sender
                player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("censored"));

                // Light point award
                PlayerData data = plugin.getFilterManager().getOrCreate(player.getUniqueId());
                plugin.getPunishmentManager().applyPoints(
                        player, data, result.getPoints(),
                        result.getFilterName(), message, result.getReason()
                );

                fileLogger.log(player.getName(), result.getFilterName() + " [CENSORED]", message);
            }

            case ALLOW -> {
                // Message is clean, let it through
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Clean up player data after quit to save memory
        // Keep it for a bit in case of quick rejoin — just remove tracking queue
        plugin.getFilterManager().getOrCreate(event.getPlayer().getUniqueId())
            .getRecentMessages().clear();
    }
}
