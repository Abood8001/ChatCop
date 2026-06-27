package dev.chatcop.util;

import dev.chatcop.ChatCop;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, fully async update checker. It only reads the latest published
 * version from the platform's public API and reports it — it never downloads
 * or installs anything. Fails silently if the API is unreachable.
 */
public class UpdateChecker {

    private static final int SPIGOT_RESOURCE_ID = 135758;
    private static final String MODRINTH_SLUG = "chatcop";

    private final ChatCop plugin;
    private volatile boolean updateAvailable;
    private volatile String latestVersion;

    public UpdateChecker(ChatCop plugin) {
        this.plugin = plugin;
    }

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String getLatestVersion()   { return latestVersion; }

    public String getDownloadUrl() {
        return isModrinth()
                ? "https://modrinth.com/plugin/" + MODRINTH_SLUG
                : "https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID;
    }

    /** Runs the check off the main thread and logs the result to console. */
    public void checkAsync() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) return;
        Scheduler.async(plugin, this::check);
    }

    private void check() {
        try {
            String current = plugin.getDescription().getVersion();
            String remote  = isModrinth() ? fetchModrinth() : fetchSpigot();
            if (remote == null || remote.isEmpty()) return;

            if (isNewer(remote, current)) {
                updateAvailable = true;
                latestVersion = remote;
                plugin.getLogger().info("A new version is available: v" + remote
                        + " (running v" + current + ").");
                plugin.getLogger().info("Download: " + getDownloadUrl());
            } else if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[UpdateChecker] Up to date (v" + current + ").");
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug())
                plugin.getLogger().warning("[UpdateChecker] Check failed: " + e.getMessage());
        }
    }

    private boolean isModrinth() {
        return "modrinth".equalsIgnoreCase(
                plugin.getConfig().getString("update-checker.source", "spigot"));
    }

    private String fetchSpigot() throws Exception {
        // Spiget exposes Spigot resource data without authentication.
        String json = get("https://api.spiget.org/v2/resources/" + SPIGOT_RESOURCE_ID + "/versions/latest");
        return extract(json, "\"name\"\\s*:\\s*\"([^\"]+)\"");
    }

    private String fetchModrinth() throws Exception {
        // Returns versions newest-first; grab the first version_number.
        String json = get("https://api.modrinth.com/v2/project/" + MODRINTH_SLUG + "/version");
        return extract(json, "\"version_number\"\\s*:\\s*\"([^\"]+)\"");
    }

    private String get(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        // A descriptive User-Agent is appreciated by Spiget and required by Modrinth.
        conn.setRequestProperty("User-Agent",
                "ChatCop/" + plugin.getDescription().getVersion() + " (update checker)");
        conn.setRequestProperty("Accept", "application/json");
        if (conn.getResponseCode() != 200) { conn.disconnect(); return null; }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        conn.disconnect();
        return sb.toString();
    }

    private String extract(String json, String regex) {
        if (json == null) return null;
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? m.group(1).trim() : null;
    }

    /** True if remote is a strictly higher version than current (dotted-numeric compare). */
    private boolean isNewer(String remote, String current) {
        int[] r = parse(remote), c = parse(current);
        int len = Math.max(r.length, c.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (rv != cv) return rv > cv;
        }
        return false;
    }

    private int[] parse(String v) {
        String[] parts = v.replaceAll("[^0-9.]", "").split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) { out[i] = 0; }
        }
        return out;
    }
}
