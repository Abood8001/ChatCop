package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.model.MuteEntry;
import dev.chatcop.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MuteManager {

    private final ChatCop plugin;
    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();

    /** Lower-cased name to UUID, so offline players can be muted and unmuted. */
    private final Map<String, UUID> nameCache = new ConcurrentHashMap<>();

    private final Object fileLock = new Object();
    private File dataFile;
    private volatile boolean persistent;

    public MuteManager(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        persistent = plugin.getConfig().getBoolean("mutes.persistent", true);
        String path = plugin.getConfig().getString("mutes.storage-file", "data/mutes.yml");
        dataFile = new File(plugin.getDataFolder(), path);
        File parent = dataFile.getParentFile();
        if (parent != null) parent.mkdirs();

        if (!persistent || !dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                MuteEntry entry = new MuteEntry(
                        uuid,
                        yaml.getString(key + ".name", "Unknown"),
                        yaml.getString(key + ".reason", ""),
                        yaml.getString(key + ".by", "CONSOLE"),
                        yaml.getLong(key + ".muteTime"),
                        yaml.getLong(key + ".expireTime"));
                if (!entry.isExpired()) {
                    mutes.put(uuid, entry);
                    cacheName(entry.getPlayerName(), uuid);
                }
            } catch (Exception ignored) {
                // A corrupt row shouldn't stop the rest of the file loading.
            }
        }
    }

    /**
     * Re-reads settings without dropping active mutes.
     *
     * The old version cleared the map and reloaded from disk, so with
     * mutes.persistent set to false every active mute silently vanished on
     * /chatcop reload.
     */
    public void reload() {
        boolean wasPersistent = persistent;
        Map<UUID, MuteEntry> carried = new ConcurrentHashMap<>(mutes);

        mutes.clear();
        load();

        if (!wasPersistent || !persistent) {
            carried.forEach((uuid, entry) -> {
                if (!entry.isExpired()) mutes.putIfAbsent(uuid, entry);
            });
        }
    }

    public void cacheName(String name, UUID uuid) {
        if (name != null && !name.isBlank() && uuid != null) {
            nameCache.put(name.toLowerCase(Locale.ROOT), uuid);
        }
    }

    /**
     * Resolves a player name to a UUID without blocking, then hands the result
     * to the callback on the main thread. Online players and anyone in the
     * name cache resolve immediately; everyone else goes through the server's
     * offline lookup on an async thread.
     */
    public void resolvePlayer(String name, Consumer<UUID> callback) {
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            cacheName(online.getName(), online.getUniqueId());
            callback.accept(online.getUniqueId());
            return;
        }

        UUID cached = nameCache.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        Scheduler.async(plugin, () -> {
            UUID resolved = null;
            try {
                @SuppressWarnings("deprecation")
                OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
                if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                    resolved = offline.getUniqueId();
                    cacheName(name, resolved);
                }
            } catch (Exception ignored) {
                // Unknown name; reported to the caller as null.
            }
            final UUID result = resolved;
            Scheduler.global(plugin, () -> callback.accept(result));
        });
    }

    public void saveMutes() {
        if (!persistent) return;

        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, MuteEntry> e : mutes.entrySet()) {
            MuteEntry m = e.getValue();
            if (m.isExpired()) continue;
            String key = e.getKey().toString();
            yaml.set(key + ".name", m.getPlayerName());
            yaml.set(key + ".reason", m.getReason());
            yaml.set(key + ".by", m.getMutedBy());
            yaml.set(key + ".muteTime", m.getMuteTime());
            yaml.set(key + ".expireTime", m.getExpireTime());
        }

        synchronized (fileLock) {
            try {
                yaml.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save mutes: " + e.getMessage());
            }
        }
    }

    /** Persists off the calling thread so a command never blocks on disk I/O. */
    private void saveAsync() {
        if (!persistent) return;
        Scheduler.async(plugin, this::saveMutes);
    }

    public void mute(MuteEntry entry) {
        mutes.put(entry.getPlayerUuid(), entry);
        cacheName(entry.getPlayerName(), entry.getPlayerUuid());
        saveAsync();
    }

    public boolean unmute(UUID uuid) {
        boolean removed = mutes.remove(uuid) != null;
        if (removed) saveAsync();
        return removed;
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null) return false;
        if (entry.isExpired()) {
            mutes.remove(uuid);
            saveAsync();
            return false;
        }
        return true;
    }

    public MuteEntry getMute(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        return (entry != null && entry.isExpired()) ? null : entry;
    }

    public List<MuteEntry> getActiveMutes() {
        List<MuteEntry> out = new ArrayList<>();
        for (MuteEntry m : mutes.values()) {
            if (!m.isExpired()) out.add(m);
        }
        out.sort((a, b) -> Long.compare(b.getMuteTime(), a.getMuteTime()));
        return out;
    }

    public int getMuteCount() { return getActiveMutes().size(); }
}
