package dev.chatcop.config;

import dev.chatcop.ChatCop;
import org.bukkit.configuration.file.FileConfiguration;
import dev.chatcop.util.ColorUtil;

public class ConfigManager {

    private final ChatCop plugin;
    private FileConfiguration cfg;

    public ConfigManager(ChatCop plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        this.cfg = plugin.getConfig();
    }

    public FileConfiguration get() { return cfg; }

    public String getPrefix() {
        return ColorUtil.translate(cfg.getString("prefix", "&8[&bChat&3Cop&8] "));
    }

    public String getMessage(String key) {
        return ColorUtil.translate(cfg.getString("messages." + key, "&cMessage not found: " + key));
    }

    public String getMessage(String key, String... placeholders) {
        String msg = getMessage(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        return msg;
    }

    public String getAlertFormat() {
        return ColorUtil.translate(cfg.getString("notifications.alert-format",
                "&8[&cAlert&8] &7%player% &8| &f%filter% &8| &7\"%message%\""));
    }

    public boolean isDebug() { return cfg.getBoolean("general.debug", false); }
    public boolean isLogToFile() { return cfg.getBoolean("general.log-to-file", true); }
    public boolean isSilentBlock() { return cfg.getBoolean("general.silent-block", false); }
    public boolean isStaffAlertsEnabled() { return cfg.getBoolean("notifications.staff-alerts", true); }
    public boolean isAlertSoundEnabled() { return cfg.getBoolean("notifications.alert-sound", true); }
    public String getAlertSound() { return cfg.getString("notifications.alert-sound-type", "BLOCK_NOTE_BLOCK_PLING"); }

    public static String color(String s) {
        return ColorUtil.translate(s);
    }
}
