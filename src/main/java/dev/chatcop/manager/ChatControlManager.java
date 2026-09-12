package dev.chatcop.manager;

import dev.chatcop.ChatCop;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide chat controls that sit in front of the filters: slowmode, a
 * global chat lock, and a delay before brand-new accounts may talk.
 *
 * The join delay is the useful one against bot raids — throwaway accounts
 * usually spam within a second or two of connecting.
 */
public class ChatControlManager {

    private final ChatCop plugin;

    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTime = new ConcurrentHashMap<>();

    private volatile boolean chatLocked;
    private volatile long slowmodeMs;
    private volatile long firstJoinDelayMs;

    public ChatControlManager(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    public final void load() {
        slowmodeMs = Math.max(0, plugin.getConfig().getLong("chat-control.slowmode-seconds", 0)) * 1000L;
        firstJoinDelayMs = Math.max(0, plugin.getConfig().getLong("chat-control.first-join-delay-seconds", 0)) * 1000L;
        chatLocked = plugin.getConfig().getBoolean("chat-control.locked-on-start", false);
    }

    public void reload() { load(); }

    public void trackJoin(Player player) {
        joinTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void forget(UUID uuid) {
        lastMessage.remove(uuid);
        joinTime.remove(uuid);
    }

    public boolean isChatLocked() { return chatLocked; }

    public void setChatLocked(boolean locked) { this.chatLocked = locked; }

    public long getSlowmodeSeconds() { return slowmodeMs / 1000L; }

    public void setSlowmodeSeconds(long seconds) {
        this.slowmodeMs = Math.max(0, seconds) * 1000L;
    }

    /**
     * @return seconds the player must still wait, or 0 if they may talk now.
     */
    public long getJoinDelayRemaining(Player player) {
        if (firstJoinDelayMs <= 0) return 0;
        if (player.hasPermission("chatcop.bypass.joindelay")) return 0;

        Long joined = joinTime.get(player.getUniqueId());
        if (joined == null) return 0;

        long elapsed = System.currentTimeMillis() - joined;
        if (elapsed >= firstJoinDelayMs) return 0;
        return Math.max(1, (firstJoinDelayMs - elapsed + 999) / 1000);
    }

    /**
     * @return seconds left on the player's slowmode cooldown, or 0 if clear.
     *         Arms the cooldown when it returns 0.
     */
    public long consumeSlowmode(Player player) {
        if (slowmodeMs <= 0) return 0;
        if (player.hasPermission("chatcop.bypass.slowmode")) return 0;

        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < slowmodeMs) {
            return Math.max(1, (slowmodeMs - (now - last) + 999) / 1000);
        }
        lastMessage.put(player.getUniqueId(), now);
        return 0;
    }
}
