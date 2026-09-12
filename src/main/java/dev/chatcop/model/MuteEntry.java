package dev.chatcop.model;

import java.util.UUID;

public class MuteEntry {

    private final UUID playerUuid;
    private final String playerName;
    private final String reason;
    private final String mutedBy;
    private final long muteTime;
    private final long expireTime; // -1 = permanent

    public MuteEntry(UUID playerUuid, String playerName, String reason, String mutedBy, long durationMillis) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.mutedBy = mutedBy;
        this.muteTime = System.currentTimeMillis();
        this.expireTime = durationMillis <= 0 ? -1 : muteTime + durationMillis;
    }

    /** Constructor for loading from storage. */
    public MuteEntry(UUID playerUuid, String playerName, String reason, String mutedBy,
                     long muteTime, long expireTime) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.mutedBy = mutedBy;
        this.muteTime = muteTime;
        this.expireTime = expireTime;
    }

    public boolean isExpired() {
        return expireTime != -1 && System.currentTimeMillis() > expireTime;
    }

    public boolean isPermanent() { return expireTime == -1; }

    public long getRemainingMillis() {
        if (isPermanent()) return -1;
        return Math.max(0, expireTime - System.currentTimeMillis());
    }

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
    public String getReason() { return reason == null ? "" : reason; }
    public String getMutedBy() { return mutedBy == null ? "CONSOLE" : mutedBy; }
    public long getMuteTime() { return muteTime; }
    public long getExpireTime() { return expireTime; }
}
