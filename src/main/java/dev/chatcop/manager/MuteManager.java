package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.model.MuteEntry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MuteManager {

    private final ChatCop plugin;
    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();
    private File dataFile;
    private boolean persistent;

    public MuteManager(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        persistent = plugin.getConfig().getBoolean("mutes.persistent", true);
        String path = plugin.getConfig().getString("mutes.storage-file", "data/mutes.yml");
        dataFile = new File(plugin.getDataFolder(), path);
        dataFile.getParentFile().mkdirs();

        if (persistent && dataFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            for (String key : yaml.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String name    = yaml.getString(key + ".name", "Unknown");
                    String reason  = yaml.getString(key + ".reason", "");
                    long muteTime  = yaml.getLong(key + ".muteTime");
                    long expireTime = yaml.getLong(key + ".expireTime");
                    MuteEntry entry = new MuteEntry(uuid, name, reason, muteTime, expireTime);
                    if (!entry.isExpired()) mutes.put(uuid, entry);
                } catch (Exception ignored) {}
            }
        }
    }

    public void reload() {
        mutes.clear();
        load();
    }

    public void saveMutes() {
        if (!persistent) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, MuteEntry> e : mutes.entrySet()) {
            String key = e.getKey().toString();
            MuteEntry m = e.getValue();
            if (m.isExpired()) continue;
            yaml.set(key + ".name", m.getPlayerName());
            yaml.set(key + ".reason", m.getReason());
            yaml.set(key + ".muteTime", m.getMuteTime());
            yaml.set(key + ".expireTime", m.getExpireTime());
        }
        try { yaml.save(dataFile); } catch (IOException e) {
            plugin.getLogger().warning("Failed to save mutes: " + e.getMessage());
        }
    }

    public void mute(MuteEntry entry) {
        mutes.put(entry.getPlayerUuid(), entry);
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null) return false;
        if (entry.isExpired()) {
            mutes.remove(uuid);
            return false;
        }
        return true;
    }

    public MuteEntry getMute(UUID uuid) {
        return mutes.get(uuid);
    }

    public int getMuteCount() {
        return (int) mutes.values().stream().filter(m -> !m.isExpired()).count();
    }
}
