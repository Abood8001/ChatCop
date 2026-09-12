package dev.chatcop.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Per-player tracking data. All mutating methods are synchronized because
 * chat events fire asynchronously and the same player can be processed
 * on multiple threads at once.
 */
public class PlayerData {

    private static final int MAX_CONTENT_ENTRIES = 10;
    private static final int MAX_HISTORY = 20;

    private final UUID uuid;
    private int points;
    private long lastPointDecay;
    private int warnCount;
    private int decayPerMinute = 2;

    /**
     * How long message timestamps are kept for flood detection. Driven by the
     * configured flood-window rather than a fixed 10 seconds — that constant
     * silently capped the window, so flood-window: 30 only ever saw 10s of
     * history.
     */
    private long retentionMs = 10_000L;

    private long punishmentCooldownUntil = 0;
    private int lastPunishThreshold = 0;
    private boolean dirty = false;

    private final Deque<Long> recentMessages = new ArrayDeque<>();
    private final List<String> recentContent = new ArrayList<>();
    private final List<Long> recentContentTimes = new ArrayList<>();
    private final List<String> violationHistory = new ArrayList<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.lastPointDecay = System.currentTimeMillis();
    }

    public synchronized void setDecayPerMinute(int decayPerMinute) {
        this.decayPerMinute = decayPerMinute;
    }

    /** Keeps at least the flood window, plus headroom, so pruning never truncates it. */
    public synchronized void setRetentionMs(long windowMs) {
        this.retentionMs = Math.max(10_000L, windowMs * 2);
    }

    public synchronized void addPoints(int pts) {
        decayPoints();
        this.points += pts;
        this.dirty = true;
    }

    public synchronized void decayPoints() {
        long now = System.currentTimeMillis();
        long minutesPassed = (now - lastPointDecay) / 60000;
        if (minutesPassed > 0) {
            int before = points;
            points = Math.max(0, points - (int) (minutesPassed * decayPerMinute));
            lastPointDecay = now;
            if (before != points) dirty = true;
        }
    }

    /** Records the message timestamp for flood detection (called for every message). */
    public synchronized void trackTimestamp() {
        long now = System.currentTimeMillis();
        recentMessages.addLast(now);
        while (!recentMessages.isEmpty() && now - recentMessages.peekFirst() > retentionMs) {
            recentMessages.pollFirst();
        }
    }

    /**
     * Records message content for duplicate detection. The caller passes the
     * normalized form so comparisons happen between like and like.
     */
    public synchronized void trackContent(String normalized, long expiryMs) {
        long now = System.currentTimeMillis();
        recentContent.add(normalized);
        recentContentTimes.add(now);

        while (!recentContentTimes.isEmpty() && now - recentContentTimes.get(0) > expiryMs) {
            recentContentTimes.remove(0);
            recentContent.remove(0);
        }
        while (recentContent.size() > MAX_CONTENT_ENTRIES) {
            recentContent.remove(0);
            recentContentTimes.remove(0);
        }
    }

    public synchronized void addViolation(String desc) {
        violationHistory.add(0, desc);
        while (violationHistory.size() > MAX_HISTORY) {
            violationHistory.remove(violationHistory.size() - 1);
        }
        dirty = true;
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

    public synchronized int getLastPunishThreshold() { return lastPunishThreshold; }

    public synchronized void setLastPunishThreshold(int threshold) {
        this.lastPunishThreshold = threshold;
        this.dirty = true;
    }

    public synchronized List<String> getRecentContent() { return new ArrayList<>(recentContent); }
    public synchronized List<String> getViolationHistory() { return new ArrayList<>(violationHistory); }

    public UUID getUuid() { return uuid; }
    public synchronized int getPoints() { decayPoints(); return points; }
    public synchronized int getWarnCount() { return warnCount; }

    public synchronized void incrementWarnCount() { warnCount++; dirty = true; }

    public synchronized void resetPoints() {
        points = 0;
        lastPunishThreshold = 0;
        dirty = true;
    }

    public synchronized void clearHistory() { violationHistory.clear(); dirty = true; }

    // ── persistence ───────────────────────────────────────────────────────────

    public synchronized boolean isDirty() { return dirty; }
    public synchronized void clearDirty() { dirty = false; }

    /** True when nothing about this player is worth writing to disk. */
    public synchronized boolean isEmpty() {
        return points == 0 && warnCount == 0 && violationHistory.isEmpty() && lastPunishThreshold == 0;
    }

    public synchronized long getLastPointDecay() { return lastPointDecay; }

    public synchronized void restore(int points, int warnCount, int lastPunishThreshold,
                                     long lastPointDecay, List<String> history) {
        this.points = Math.max(0, points);
        this.warnCount = Math.max(0, warnCount);
        this.lastPunishThreshold = Math.max(0, lastPunishThreshold);
        this.lastPointDecay = lastPointDecay > 0 ? lastPointDecay : System.currentTimeMillis();
        this.violationHistory.clear();
        if (history != null) {
            for (String h : history) {
                if (violationHistory.size() >= MAX_HISTORY) break;
                this.violationHistory.add(h);
            }
        }
        this.dirty = false;
    }
}
