package dev.chatcop.filter;

import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import org.bukkit.entity.Player;

public interface ChatFilter {

    /**
     * Analyze the message and return a FilterResult.
     *
     * @param player  the sender. May be null when the message is being tested
     *                rather than sent (/chatcop test from console), so
     *                implementations must not dereference it unconditionally.
     * @param message the raw message
     * @param data    the player's tracking data; a throwaway instance during a test
     */
    FilterResult analyze(Player player, String message, PlayerData data);

    /** Unique filter name shown in alerts/logs. */
    String getName();

    /** Whether this filter is currently enabled. */
    boolean isEnabled();
}
