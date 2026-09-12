package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import dev.chatcop.filter.*;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.TextNormalizer;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FilterManager {

    private final ChatCop plugin;

    /**
     * Volatile so a reload can swap in a fully built list atomically. The old
     * code cleared and refilled a plain ArrayList while the async chat thread
     * was iterating it, which could throw ConcurrentModificationException or
     * skip filters mid-message.
     */
    private volatile List<ChatFilter> filters = List.of();

    private final ConcurrentHashMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    // Cached per reload rather than read from config on every message.
    private volatile long duplicateExpiryMs = 8000L;
    private volatile long floodWindowMs = 3000L;
    private volatile boolean crossPlayerEnabled = true;
    private volatile int crossPlayerThreshold = 4;
    private volatile long crossPlayerWindowMs = 10_000L;
    private volatile double crossPlayerSimilarity = 0.90;
    private volatile int crossPlayerPoints = 6;

    /** Recent messages server-wide, for detecting the same line from many accounts. */
    private final Deque<GlobalMessage> globalRecent = new ArrayDeque<>();

    public FilterManager(ChatCop plugin) {
        this.plugin = plugin;
        loadFilters();
    }

    private void loadFilters() {
        // Order matters: cheapest and most-likely checks first.
        List<ChatFilter> built = new ArrayList<>(5);
        SpamFilter spam = new SpamFilter(plugin);
        built.add(spam);
        built.add(new AdvertisingFilter(plugin));
        built.add(new ThreatFilter(plugin));
        built.add(new ToxicityFilter(plugin));
        built.add(new ProfanityFilter(plugin));

        duplicateExpiryMs = Math.max(1L, plugin.getConfig().getLong("filters.spam.duplicate-content-expiry", 8)) * 1000L;
        floodWindowMs = spam.getFloodWindowMs();

        crossPlayerEnabled    = plugin.getConfig().getBoolean("filters.spam.cross-player.enabled", true);
        crossPlayerThreshold  = Math.max(2, plugin.getConfig().getInt("filters.spam.cross-player.player-threshold", 4));
        crossPlayerWindowMs   = Math.max(1, plugin.getConfig().getLong("filters.spam.cross-player.window", 10)) * 1000L;
        crossPlayerSimilarity = plugin.getConfig().getDouble("filters.spam.cross-player.similarity", 0.90);
        crossPlayerPoints     = plugin.getConfig().getInt("filters.spam.cross-player.points", 6);

        this.filters = List.copyOf(built);
    }

    public void reload() {
        loadFilters();
        synchronized (globalRecent) { globalRecent.clear(); }
    }

    public FilterResult process(Player player, String message) {
        return process(player, message, false);
    }

    /**
     * Runs the enabled filters against a message.
     *
     * @param dryRun when true nothing is recorded — no flood timestamps, no
     *               duplicate history, no raid tracking. Used by /chatcop test
     *               so an admin testing phrases doesn't pollute their own spam
     *               state (or trip their own flood filter).
     */
    public FilterResult process(Player player, String message, boolean dryRun) {
        PlayerData data = getOrCreate(player.getUniqueId());
        data.setRetentionMs(floodWindowMs);
        if (!dryRun) data.trackTimestamp();

        FilterResult censorResult = null;

        for (ChatFilter filter : filters) {
            if (!filter.isEnabled()) continue;
            if (hasFilterBypass(player, filter.getName())) continue;

            FilterResult result = filter.analyze(player, message, data);
            if (result.getAction() == FilterResult.Action.BLOCK) {
                return result; // Hard block wins immediately.
            }
            if (result.getAction() == FilterResult.Action.CENSOR && censorResult == null) {
                censorResult = result; // Remember the first censor, keep checking for a block.
            }
        }

        String normalized = TextNormalizer.normalize(message);

        if (crossPlayerEnabled && !hasFilterBypass(player, "Spam")) {
            FilterResult raid = checkCrossPlayer(player.getUniqueId(), normalized, dryRun);
            if (raid != null) return raid;
        }

        if (!dryRun) data.trackContent(normalized, duplicateExpiryMs);
        return censorResult != null ? censorResult : FilterResult.allow();
    }

    /**
     * Analyses a message without a sender and without touching any state.
     * Backs /chatcop test, so an admin can check phrases from the console and
     * without polluting their own flood window or duplicate history.
     */
    public FilterResult test(String message) {
        PlayerData scratch = new PlayerData(new UUID(0L, 0L));
        FilterResult censorResult = null;

        for (ChatFilter filter : filters) {
            if (!filter.isEnabled()) continue;
            FilterResult result = filter.analyze(null, message, scratch);
            if (result.getAction() == FilterResult.Action.BLOCK) return result;
            if (result.getAction() == FilterResult.Action.CENSOR && censorResult == null) {
                censorResult = result;
            }
        }
        return censorResult != null ? censorResult : FilterResult.allow();
    }

    /**
     * Catches the same line arriving from several accounts at once, which is
     * what a bot raid looks like. Per-player tracking never sees it, because
     * each account only sends the message once.
     */
    private FilterResult checkCrossPlayer(UUID sender, String normalized, boolean dryRun) {
        if (normalized.length() < 6) return null;

        long now = System.currentTimeMillis();
        Set<UUID> senders = new HashSet<>();

        synchronized (globalRecent) {
            while (!globalRecent.isEmpty() && now - globalRecent.peekFirst().time > crossPlayerWindowMs) {
                globalRecent.pollFirst();
            }
            for (GlobalMessage gm : globalRecent) {
                if (TextNormalizer.similarity(normalized, gm.content) >= crossPlayerSimilarity) {
                    senders.add(gm.sender);
                }
            }
            if (!dryRun) {
                globalRecent.addLast(new GlobalMessage(sender, normalized, now));
                while (globalRecent.size() > 200) globalRecent.pollFirst();
            }
        }

        senders.add(sender);
        if (senders.size() >= crossPlayerThreshold) {
            return FilterResult.block("Spam", "Coordinated spam detected", crossPlayerPoints);
        }
        return null;
    }

    /** chatcop.bypass.<filter>, e.g. chatcop.bypass.advertising. */
    private boolean hasFilterBypass(Player player, String filterName) {
        return player.hasPermission("chatcop.bypass." + filterName.toLowerCase());
    }

    public PlayerData getOrCreate(UUID uuid) {
        return playerData.computeIfAbsent(uuid, id -> {
            PlayerData data = new PlayerData(id);
            PlayerDataStore store = plugin.getPlayerDataStore();
            if (store != null) store.restore(id, data);
            return data;
        });
    }

    /** Already-tracked data only; never creates an entry. */
    public PlayerData getIfPresent(UUID uuid) {
        return playerData.get(uuid);
    }

    public void removePlayer(UUID uuid) {
        PlayerData data = playerData.remove(uuid);
        PlayerDataStore store = plugin.getPlayerDataStore();
        if (data != null && store != null) store.stage(uuid, data);
    }

    /** Stages every dirty player so a periodic flush has something to write. */
    public void stageAll() {
        PlayerDataStore store = plugin.getPlayerDataStore();
        if (store == null) return;
        playerData.forEach((uuid, data) -> {
            if (data.isDirty()) store.stage(uuid, data);
        });
    }

    public int getTrackedPlayerCount() { return playerData.size(); }

    public List<String> getFilterNames() {
        List<String> names = new ArrayList<>();
        for (ChatFilter f : filters) names.add(f.getName());
        return names;
    }

    private record GlobalMessage(UUID sender, String content, long time) {}
}
