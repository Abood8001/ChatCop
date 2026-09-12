package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.util.ColorUtil;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discord webhook alerts.
 *
 * Payloads go through a bounded queue drained by a single timed task rather
 * than firing one HTTP request per flagged message: a spam wave used to mean a
 * POST per message, which Discord answers with 429s.
 */
public class DiscordWebhookManager {

    private static final Pattern RETRY_AFTER =
            Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");

    private final ChatCop plugin;
    private final Deque<String> queue = new ArrayDeque<>();

    private volatile boolean enabled;
    private volatile String webhookUrl = "";
    private volatile int queueLimit = 50;
    private volatile long blockedUntil = 0L;
    private long droppedSinceWarn = 0L;

    public DiscordWebhookManager(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    public final void load() {
        enabled    = plugin.getConfig().getBoolean("discord.enabled", false);
        webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        queueLimit = Math.max(5, plugin.getConfig().getInt("discord.queue-limit", 50));
    }

    public void reload() { load(); }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isEmpty() && !webhookUrl.contains("YOUR_WEBHOOK");
    }

    /** Alert for an automatic filter hit. */
    public void sendAlert(Player player, String filterName, String message, String reason, String punisher) {
        if (!isEnabled()) return;
        if (!plugin.getConfig().getBoolean("discord.filters." + filterName + ".enabled", true)) return;

        int color = plugin.getConfig().getInt("discord.filters." + filterName + ".color",
                plugin.getConfig().getInt("discord.default-color", 16711680));

        String fields = field("🔍 Filter", filterName, true)
                + "," + field("👤 Player", player.getName(), true)
                + "," + field("⚖️ Punisher", punisher, true)
                + "," + field("📋 Reason", ColorUtil.strip(reason), true)
                + "," + field("🌍 World", player.getWorld().getName(), true)
                + "," + field("💬 Message", "```" + sanitize(ColorUtil.strip(message)) + "```", false);

        enqueue(embed("🚨 Chat Violation Detected", color, fields));
    }

    /** Alert for a manual staff action (mute, unmute, warn). */
    public void sendAction(String actor, String action, String targetName, String detail, String reason) {
        if (!isEnabled()) return;
        if (!plugin.getConfig().getBoolean("discord.staff-actions", true)) return;

        int color = plugin.getConfig().getInt("discord.action-color", 3447003);

        StringBuilder fields = new StringBuilder()
                .append(field("⚖️ Action", action, true))
                .append(",").append(field("👤 Player", targetName, true))
                .append(",").append(field("👮 Staff", actor, true));
        if (detail != null && !detail.isBlank()) {
            fields.append(",").append(field("⏱️ Duration", detail, true));
        }
        if (reason != null && !reason.isBlank()) {
            fields.append(",").append(field("📋 Reason", ColorUtil.strip(reason), false));
        }

        enqueue(embed("🛡️ Staff Action", color, fields.toString()));
    }

    private String embed(String title, int color, String fields) {
        String thumbnail  = plugin.getConfig().getString("discord.thumbnail", "");
        String footerIcon = plugin.getConfig().getString("discord.footer-icon", "");

        String thumbnailJson = (thumbnail == null || thumbnail.isEmpty())
                ? "" : ",\"thumbnail\":{\"url\":\"" + escape(thumbnail) + "\"}";

        String footerJson = (footerIcon == null || footerIcon.isEmpty())
                ? "\"footer\":{\"text\":\"ChatCop\"}"
                : "\"footer\":{\"text\":\"ChatCop\",\"icon_url\":\"" + escape(footerIcon) + "\"}";

        return "{"
                + "\"username\":\"ChatCop\","
                // Without this, a player typing "@everyone" in a flagged message
                // pings the whole Discord server when the alert is relayed.
                + "\"allowed_mentions\":{\"parse\":[]},"
                + "\"embeds\":[{"
                + "\"title\":\"" + escape(title) + "\","
                + "\"color\":" + color + ","
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"fields\":[" + fields + "]"
                + thumbnailJson
                + "," + footerJson
                + "}]}";
    }

    private void enqueue(String payload) {
        synchronized (queue) {
            if (queue.size() >= queueLimit) {
                queue.pollFirst();
                droppedSinceWarn++;
            }
            queue.addLast(payload);
        }
    }

    /**
     * Sends at most one queued payload. Driven by a repeating async task, so
     * bursts are spread out instead of hammering the webhook.
     */
    public void drainOne() {
        if (!isEnabled()) return;
        if (System.currentTimeMillis() < blockedUntil) return;

        String payload;
        long dropped;
        synchronized (queue) {
            payload = queue.pollFirst();
            dropped = droppedSinceWarn;
            droppedSinceWarn = 0;
        }
        if (dropped > 0) {
            plugin.getLogger().warning("[Discord] Dropped " + dropped
                    + " queued alert(s); the webhook can't keep up with the current volume.");
        }
        if (payload == null) return;

        post(payload);
    }

    private void post(String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "ChatCop/" + plugin.getDescription().getVersion());
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 429) {
                blockedUntil = System.currentTimeMillis() + readRetryAfter(conn);
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().warning("[Discord] Rate limited; pausing alerts briefly.");
                }
            } else if (code >= 400 && plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[Discord] Webhook returned: " + code);
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[Discord] Failed: " + e.getMessage());
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private long readRetryAfter(HttpURLConnection conn) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            Matcher m = RETRY_AFTER.matcher(sb);
            if (m.find()) return (long) (Double.parseDouble(m.group(1)) * 1000L);
        } catch (Exception ignored) {
            // Fall through to the default backoff.
        }
        return 5000L;
    }

    private String field(String name, String value, boolean inline) {
        return "{\"name\":\"" + escape(name) + "\",\"value\":\"" + escape(value) + "\",\"inline\":" + inline + "}";
    }

    private String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> { /* drop */ }
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.toString();
    }

    private String sanitize(String s) {
        return s == null ? "" : s.replace("`", "'");
    }
}
