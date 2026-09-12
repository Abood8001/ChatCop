package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.model.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * Persists violation points, warning counts and violation history across
 * restarts.
 *
 * Previously all of this lived in memory only and was dropped 60 seconds after
 * a player quit, so "/chatcop history" could only ever show the current
 * session and a restart wiped every player's accumulated points.
 */
public class PlayerDataStore {

    private final ChatCop plugin;
    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration yaml;
    private volatile boolean enabled;

    public PlayerDataStore(ChatCop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/players.yml");
        load();
    }

    public final void load() {
        enabled = plugin.getConfig().getBoolean("player-data.persistent", true);
        synchronized (lock) {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        }
    }

    public void reload() { load(); }

    /** Restores stored counters onto a freshly created PlayerData. */
    public void restore(UUID uuid, PlayerData data) {
        if (!enabled) return;

        ConfigurationSection s;
        synchronized (lock) {
            s = yaml.getConfigurationSection(uuid.toString());
            if (s == null) return;

            int points     = s.getInt("points", 0);
            int warns      = s.getInt("warns", 0);
            int threshold  = s.getInt("threshold", 0);
            long lastDecay = s.getLong("last-decay", System.currentTimeMillis());
            List<String> history = s.getStringList("history");
            data.restore(points, warns, threshold, lastDecay, history);
        }
    }

    /** Writes one player's counters into the in-memory document. */
    public void stage(UUID uuid, PlayerData data) {
        if (!enabled) return;

        synchronized (lock) {
            String key = uuid.toString();
            if (data.isEmpty()) {
                yaml.set(key, null);
            } else {
                yaml.set(key + ".points", data.getPoints());
                yaml.set(key + ".warns", data.getWarnCount());
                yaml.set(key + ".threshold", data.getLastPunishThreshold());
                yaml.set(key + ".last-decay", data.getLastPointDecay());
                yaml.set(key + ".history", data.getViolationHistory());
            }
        }
        data.clearDirty();
    }

    /** Reads one player's stored counters without needing them loaded in memory. */
    public StoredSnapshot peek(UUID uuid) {
        synchronized (lock) {
            ConfigurationSection s = yaml.getConfigurationSection(uuid.toString());
            if (s == null) return null;
            return new StoredSnapshot(
                    s.getInt("points", 0),
                    s.getInt("warns", 0),
                    s.getStringList("history"));
        }
    }

    public void clear(UUID uuid) {
        synchronized (lock) { yaml.set(uuid.toString(), null); }
    }

    /**
     * Flushes to disk. Call from an async context — this touches the filesystem.
     *
     * The document is serialized under the lock but written outside it, so a
     * chat message calling restore() never waits on disk I/O.
     */
    public void flush() {
        if (!enabled) return;

        String contents;
        synchronized (lock) {
            contents = yaml.saveToString();
        }

        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data: " + e.getMessage());
        }
    }

    public record StoredSnapshot(int points, int warns, List<String> history) {}
}
