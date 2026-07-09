package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class NotificationManager {

    private final ChatCop plugin;

    public NotificationManager(ChatCop plugin) {
        this.plugin = plugin;
    }

    public void alertStaff(Player offender, String filterName, String message, String reason) {
        if (!plugin.getConfigManager().isStaffAlertsEnabled()) return;

        String display = message.length() > 50 ? message.substring(0, 47) + "..." : message;
        String alert = plugin.getConfigManager().getAlertFormat()
                .replace("%player%",  offender.getName())
                .replace("%filter%",  filterName)
                .replace("%message%", display)
                .replace("%reason%",  reason)
                .replace("%world%",   offender.getWorld().getName());

        String soundName = plugin.getConfigManager().getAlertSound();
        boolean playSound = plugin.getConfigManager().isAlertSoundEnabled();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("chatcop.notify")) continue;
            p.sendMessage(alert);
            if (playSound) {
                // playSound must run on the player's own thread (required on Folia,
                // and correct on Spigot too since chat events fire asynchronously).
                Scheduler.atEntity(plugin, p, () -> {
                    try {
                        Sound sound = Sound.valueOf(soundName);
                        p.playSound(p.getLocation(), sound, 1.0f, 1.5f);
                    } catch (IllegalArgumentException ignored) {}
                });
            }
        }
    }
}
