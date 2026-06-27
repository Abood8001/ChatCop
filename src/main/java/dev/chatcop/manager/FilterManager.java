package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.filter.*;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FilterManager {

    private final ChatCop plugin;
    private final List<ChatFilter> filters = new ArrayList<>();
    private final ConcurrentHashMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    public FilterManager(ChatCop plugin) {
        this.plugin = plugin;
        loadFilters();
    }

    private void loadFilters() {
        filters.clear();
        // Order matters: fastest/most-likely checks first
        filters.add(new SpamFilter(plugin));
        filters.add(new AdvertisingFilter(plugin));
        filters.add(new ThreatFilter(plugin));
        filters.add(new ToxicityFilter(plugin));
        filters.add(new ProfanityFilter(plugin));
    }

    public void reload() {
        loadFilters();
    }

    /**
     * Run all enabled filters against a message.
     * Returns the first non-allow result (block takes priority over censor).
     */
    public FilterResult process(Player player, String message) {
        PlayerData data = getOrCreate(player.getUniqueId());
        data.trackTimestamp();

        FilterResult censorResult = null;

        for (ChatFilter filter : filters) {
            if (!filter.isEnabled()) continue;
            FilterResult result = filter.analyze(player, message, data);
            if (result.getAction() == FilterResult.Action.BLOCK) {
                return result; // Hard block wins immediately
            }
            if (result.getAction() == FilterResult.Action.CENSOR && censorResult == null) {
                censorResult = result; // Store first censor, keep checking
            }
        }
        long expiryMs = plugin.getConfig().getLong("filters.spam.duplicate-content-expiry", 8) * 1000L;
        data.trackContent(message, expiryMs);
        return censorResult != null ? censorResult : FilterResult.allow();
    }

    public PlayerData getOrCreate(UUID uuid) {
        return playerData.computeIfAbsent(uuid, PlayerData::new);
    }

    public void removePlayer(UUID uuid) {
        playerData.remove(uuid);
    }

    public int getTrackedPlayerCount() {
        return playerData.size();
    }
}
