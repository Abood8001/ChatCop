package dev.chatcop.model;

import java.util.UUID;

public class MuteEntry {

    private final UUID playerUuid;
    private final String playerName;
    private final String reason;
    private final long muteTime;
    private final long expireTime; // -1 = permanent

    public MuteEntry(UUID playerUuid, String playerName, String reason, long durationMillis) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.muteTime = System.currentTimeMillis();
        this.expireTime = durationMillis <= 0 ? -1 : muteTime + durationMillis;
    }

    // Constructor for loading from storage
    public MuteEntry(UUID playerUuid, String playerName, String reason, long muteTime, long expireTime) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.muteTime = muteTime;
        this.expireTime = expireTime;
    }

    public boolean isExpired() {
        return expireTime != -1 && System.currentTimeMillis() > expireTime;
    }

    public boolean isPermanent() { return expireTime == -1; }

    public String getRemainingTime() {
        if (isPermanent()) return "Permanent";
        long remaining = expireTime - System.currentTimeMillis();
        if (remaining <= 0) return "Expired";
        return formatDuration(remaining);
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getReason() { return reason; }
    public long getMuteTime() { return muteTime; }
    public long getExpireTime() { return expireTime; }
}
