package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.util.ColorUtil;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class DiscordWebhookManager {

    private final ChatCop plugin;

    public DiscordWebhookManager(ChatCop plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false);
    }

    public void sendAlert(Player player, String filterName, String message, String reason, String punisher) {
        if (!isEnabled()) return;
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK")) return;

        String filterPath = "discord.filters." + filterName;
        if (!plugin.getConfig().getBoolean(filterPath + ".enabled", true)) return;
        int color = plugin.getConfig().getInt(filterPath + ".color",
                plugin.getConfig().getInt("discord.default-color", 16711680));

        String thumbnail  = plugin.getConfig().getString("discord.thumbnail", "");
        String footerIcon = plugin.getConfig().getString("discord.footer-icon", "");
        String worldName  = player.getWorld().getName();
        String cleanMsg   = ColorUtil.strip(message);
        String cleanReason = ColorUtil.strip(reason);

        String thumbnailJson = thumbnail.isEmpty() ? "" :
                ",\"thumbnail\":{\"url\":\"" + thumbnail + "\"}";

        String footerJson = footerIcon.isEmpty()
                ? "\"footer\":{\"text\":\"ChatCop \u2022 " + Instant.now().toString().substring(0, 10) + "\"}"
                : "\"footer\":{\"text\":\"ChatCop \u2022 " + Instant.now().toString().substring(0, 10) + "\",\"icon_url\":\"" + footerIcon + "\"}";

        String json = "{"
                + "\"username\":\"ChatCop\","
                + "\"embeds\":[{"
                + "\"title\":\"\uD83D\uDEA8 Chat Violation Detected\","
                + "\"color\":" + color + ","
                + "\"fields\":["
                + field("\uD83D\uDD0D Filter",    filterName,       true)
                + "," + field("\uD83D\uDC64 Player",    player.getName(), true)
                + "," + field("\u2696\uFE0F Punisher",  punisher,         true)
                + "," + field("\uD83D\uDCCB Reason",    cleanReason,      true)
                + "," + field("\uD83C\uDF0D World",     worldName,        true)
                + "," + field("\uD83D\uDCAC Message",   "```" + sanitize(cleanMsg) + "```", false)
                + "]"
                + thumbnailJson
                + "," + footerJson
                + "}]}";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (plugin.getConfigManager().isDebug() && code != 204)
                    plugin.getLogger().warning("[Discord] Webhook returned: " + code);
                conn.disconnect();
            } catch (Exception e) {
                if (plugin.getConfigManager().isDebug())
                    plugin.getLogger().warning("[Discord] Failed: " + e.getMessage());
            }
        });
    }

    private String field(String name, String value, boolean inline) {
        return "{\"name\":\"" + escape(name) + "\",\"value\":\"" + escape(value) + "\",\"inline\":" + inline + "}";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String sanitize(String s) {
        return s.replace("`", "'");
    }
}