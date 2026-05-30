package dev.chatcop.filter;

import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import org.bukkit.entity.Player;

public interface ChatFilter {
    /**
     * Analyze the message and return a FilterResult.
     * @param player  The sender
     * @param message The raw message
     * @param data    The player's tracking data
     */
    FilterResult analyze(Player player, String message, PlayerData data);

    /** Unique filter name shown in alerts/logs */
    String getName();

    /** Whether this filter is currently enabled */
    boolean isEnabled();
}
