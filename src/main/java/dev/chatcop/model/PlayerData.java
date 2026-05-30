package dev.chatcop.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private int points;
    private long lastPointDecay;
    private int warnCount;

    // For spam detection
    private final Queue<Long> recentMessages = new LinkedList<>();
    private final List<String> recentContent = new ArrayList<>();

    // Violation history (last 20 entries)
    private final List<String> violationHistory = new ArrayList<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.points = 0;
        this.lastPointDecay = System.currentTimeMillis();
    }

    public void addPoints(int pts) {
        decayPoints();
        this.points += pts;
    }

    public void decayPoints() {
        long now = System.currentTimeMillis();
        long minutesPassed = (now - lastPointDecay) / 60000;
        if (minutesPassed > 0) {
            // Will be fetched from config by PunishmentManager
            points = Math.max(0, points - (int) minutesPassed * 2);
            lastPointDecay = now;
        }
    }

    public void trackMessage(String content) {
        long now = System.currentTimeMillis();
        recentMessages.add(now);
        recentContent.add(content.toLowerCase());
        // Keep only last 10
        while (recentContent.size() > 10) recentContent.remove(0);
        // Trim old timestamps
        while (!recentMessages.isEmpty() && now - recentMessages.peek() > 10000) {
            recentMessages.poll();
        }
    }

    public void addViolation(String desc) {
        violationHistory.add(0, desc);
        if (violationHistory.size() > 20) violationHistory.remove(20);
    }

    public int getMessagesInWindow(long windowMs) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long t : recentMessages) {
            if (now - t <= windowMs) count++;
        }
        return count;
    }

    public List<String> getRecentContent() { return recentContent; }
    public Queue<Long> getRecentMessages() { return recentMessages; }
    public List<String> getViolationHistory() { return violationHistory; }

    public UUID getUuid() { return uuid; }
    public int getPoints() { decayPoints(); return points; }
    public void setPoints(int points) { this.points = points; }
    public int getWarnCount() { return warnCount; }
    public void incrementWarnCount() { warnCount++; }
    public void resetPoints() { points = 0; }
}
