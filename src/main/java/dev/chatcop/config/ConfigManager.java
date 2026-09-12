package dev.chatcop.config;

import dev.chatcop.ChatCop;
import dev.chatcop.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final ChatCop plugin;
    private FileConfiguration cfg;

    // Cached: these are read on the chat hot path.
    private volatile String prefix = "";
    private volatile boolean debug;
    private volatile boolean silentBlock;

    public ConfigManager(ChatCop plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
        cache();
    }

    public void reload() {
        this.cfg = plugin.getConfig();
        cache();
    }

    private void cache() {
        prefix      = ColorUtil.translate(cfg.getString("prefix", "&8[&bChat&3Cop&8] "));
        debug       = cfg.getBoolean("general.debug", false);
        silentBlock = cfg.getBoolean("general.silent-block", false);
    }

    public FileConfiguration get() { return cfg; }

    public String getPrefix() { return prefix; }

    public String getMessage(String key) {
        String raw = cfg.getString("messages." + key);
        if (raw == null) return "";
        return ColorUtil.translate(raw);
    }

    /**
     * Fetches a message and substitutes placeholders.
     *
     * Both brace and percent forms are accepted for the same token, so
     * "{reason}" and "%reason%" both work. The README documented the percent
     * form while the shipped messages used braces, and only braces worked.
     *
     * Colour codes are translated before substitution, so player-supplied text
     * can never inject formatting.
     */
    public String getMessage(String key, String... placeholders) {
        String msg = getMessage(key);
        if (msg.isEmpty()) return msg;

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String token = placeholders[i];
            String value = placeholders[i + 1] == null ? "" : placeholders[i + 1];

            msg = msg.replace(token, value);

            String bare = token.replace("{", "").replace("}", "").replace("%", "");
            msg = msg.replace("{" + bare + "}", value).replace("%" + bare + "%", value);
        }
        return msg;
    }

    public String getAlertFormat() {
        return ColorUtil.translate(cfg.getString("notifications.alert-format",
                "&8[&cAlert&8] &7%player% &8| &f%filter% &8| &7\"%message%\""));
    }

    public boolean isDebug() { return debug; }
    public boolean isSilentBlock() { return silentBlock; }
    public boolean isLogToFile() { return cfg.getBoolean("general.log-to-file", true); }

    public static String color(String s) {
        return ColorUtil.translate(s);
    }
}
