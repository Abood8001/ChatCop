package dev.chatcop.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Per-player tracking data. All mutating methods are synchronized because
 * chat events fire asynchronously and the same player can be processed
 * on multiple threads at once.
 */
public class PlayerData {

    private final UUID uuid;
    private int points;
    private long lastPointDecay;
    private int warnCount;
    private int decayPerMinute = 2; // overridden from config

    // Cooldown: timestamp until which punishment commands are suppressed
    private long punishmentCooldownUntil = 0;

    private final Queue<Long> recentMessages = new LinkedList<>();
    private final List<String> recentContent = new ArrayList<>();
    private final List<Long> recentContentTimes = new ArrayList<>();
    private final List<String> violationHistory = new ArrayList<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.points = 0;
        this.lastPointDecay = System.currentTimeMillis();
    }

    public synchronized void setDecayPerMinute(int decayPerMinute) {
        this.decayPerMinute = decayPerMinute;
    }

    public synchronized void addPoints(int pts) {
        decayPoints();
        this.points += pts;
    }

    public synchronized void decayPoints() {
        long now = System.currentTimeMillis();
        long minutesPassed = (now - lastPointDecay) / 60000;
        if (minutesPassed > 0) {
            points = Math.max(0, points - (int) (minutesPassed * decayPerMinute));
            lastPointDecay = now;
        }
    }

    /** Records the message timestamp for flood detection (called for every message). */
    public synchronized void trackTimestamp() {
        long now = System.currentTimeMillis();
        recentMessages.add(now);
        while (!recentMessages.isEmpty() && now - recentMessages.peek() > 10000) {
            recentMessages.poll();
        }
    }

    /** Records message content for duplicate detection (called only for non-blocked messages). */
    public synchronized void trackContent(String content, long expiryMs) {
        long now = System.currentTimeMillis();
        recentContent.add(content.toLowerCase());
        recentContentTimes.add(now);
        while (!recentContentTimes.isEmpty() && now - recentContentTimes.get(0) > expiryMs) {
            recentContentTimes.remove(0);
            recentContent.remove(0);
        }
        while (recentContent.size() > 10) {
            recentContent.remove(0);
            recentContentTimes.remove(0);
        }
    }

    public synchronized void addViolation(String desc) {
        violationHistory.add(0, desc);
        while (violationHistory.size() > 20) {
            violationHistory.remove(violationHistory.size() - 1);
        }
    }

    public synchronized int getMessagesInWindow(long windowMs) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long t : recentMessages) {
            if (now - t <= windowMs) count++;
        }
        return count;
    }

    /** Returns true if punishment commands should run (cooldown not active), and arms the cooldown. */
    public synchronized boolean tryPunish(long cooldownMs) {
        long now = System.currentTimeMillis();
        if (now < punishmentCooldownUntil) return false;
        punishmentCooldownUntil = now + cooldownMs;
        return true;
    }

    public synchronized List<String> getRecentContent() { return new ArrayList<>(recentContent); }
    public synchronized List<String> getViolationHistory() { return new ArrayList<>(violationHistory); }

    public UUID getUuid() { return uuid; }
    public synchronized int getPoints() { decayPoints(); return points; }
    public synchronized void setPoints(int points) { this.points = points; }
    public synchronized int getWarnCount() { return warnCount; }
    public synchronized void incrementWarnCount() { warnCount++; }
    public synchronized void resetPoints() { points = 0; }
}
