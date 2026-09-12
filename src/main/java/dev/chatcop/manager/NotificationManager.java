package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.util.ColorUtil;
import dev.chatcop.util.Scheduler;
import dev.chatcop.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class NotificationManager {

    private final ChatCop plugin;

    // Cached per reload instead of re-read from config for every alert.
    private volatile boolean staffAlerts = true;
    private volatile boolean alertSound = true;
    private volatile boolean logToConsole = false;
    private volatile boolean notifyOffender = false;
    private volatile int maxMessageLength = 50;
    private volatile String alertFormat = "";
    private volatile String soundName = "BLOCK_NOTE_BLOCK_PLING";

    public NotificationManager(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    public final void load() {
        staffAlerts      = plugin.getConfig().getBoolean("notifications.staff-alerts", true);
        alertSound       = plugin.getConfig().getBoolean("notifications.alert-sound", true);
        logToConsole     = plugin.getConfig().getBoolean("notifications.log-to-console", true);
        notifyOffender   = plugin.getConfig().getBoolean("notifications.notify-offender", false);
        maxMessageLength = Math.max(10, plugin.getConfig().getInt("notifications.max-message-length", 50));
        soundName        = plugin.getConfig().getString("notifications.alert-sound-type", "BLOCK_NOTE_BLOCK_PLING");
        alertFormat      = plugin.getConfigManager().getAlertFormat();

        if (alertSound && !SoundUtil.isValid(soundName)) {
            plugin.getLogger().warning("notifications.alert-sound-type \"" + soundName
                    + "\" is not a sound this server knows. Staff alerts will be silent.");
        }
    }

    public void reload() { load(); }

    public void alertStaff(Player offender, String filterName, String message, String reason) {
        if (!staffAlerts) return;

        String display = message.length() > maxMessageLength
                ? message.substring(0, maxMessageLength - 3) + "..."
                : message;

        String alert = alertFormat
                .replace("%player%",  offender.getName())
                .replace("%filter%",  filterName)
                .replace("%message%", display)
                .replace("%reason%",  reason == null ? "" : reason)
                .replace("%world%",   offender.getWorld().getName());

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("chatcop.notify")) continue;
            if (!notifyOffender && p.getUniqueId().equals(offender.getUniqueId())) continue;

            p.sendMessage(alert);
            if (alertSound) {
                // Sound playback must run on the player's own thread: required on
                // Folia, and correct on Spigot too since chat events are async.
                Scheduler.atEntity(plugin, p, () -> SoundUtil.play(p, soundName, 1.0f, 1.5f));
            }
        }

        if (logToConsole) {
            plugin.getLogger().info("[" + filterName + "] " + offender.getName() + ": " + ColorUtil.strip(display));
        }
    }

    /** Staff-facing notice for a manual action (mute, unmute, warn). */
    public void alertAction(String actor, String action, String targetName, String detail) {
        if (!plugin.getConfig().getBoolean("notifications.staff-action-alerts", true)) return;

        String msg = ColorUtil.translate(plugin.getConfig().getString(
                        "notifications.action-format",
                        "&8[&bChatCop&8] &7%actor% &f%action% &7%target%&f %detail%"))
                .replace("%actor%", actor)
                .replace("%action%", action)
                .replace("%target%", targetName)
                .replace("%detail%", detail == null ? "" : detail);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("chatcop.notify")) p.sendMessage(msg);
        }
        plugin.getLogger().info(ColorUtil.strip(msg));
    }
}
