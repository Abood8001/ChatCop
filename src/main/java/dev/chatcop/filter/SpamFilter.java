package dev.chatcop.filter;

import dev.chatcop.ChatCop;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.TextNormalizer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Pattern;

public class SpamFilter implements ChatFilter {

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private int duplicateThreshold;
    private int floodMessages;
    private long floodWindow;
    private double similarityThreshold;
    private int capsThreshold;
    private int capsMinLength;
    private int capsPoints;
    private boolean repeatedChars;
    private Pattern repeatedCharsPattern;

    public SpamFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.spam");
        if (s == null) { enabled = false; return; }
        enabled              = s.getBoolean("enabled", true);
        points               = s.getInt("points", 4);
        duplicateThreshold   = s.getInt("duplicate-threshold", 2);
        floodMessages        = s.getInt("flood-messages", 5);
        floodWindow          = s.getLong("flood-window", 3) * 1000L;
        similarityThreshold  = s.getDouble("similarity-threshold", 0.80);
        capsThreshold        = s.getInt("caps-threshold", 70);
        capsMinLength        = s.getInt("caps-min-length", 8);
        capsPoints           = s.getInt("caps-points", 2);
        repeatedChars        = s.getBoolean("repeated-chars", true);
        int repeatedCharsThreshold = s.getInt("repeated-chars-threshold", 4);
        // Compile once instead of on every message.
        repeatedCharsPattern = Pattern.compile(".*?(.)\\1{" + repeatedCharsThreshold + ",}.*", Pattern.DOTALL);
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        // 1. Flood check
        int msgCount = data.getMessagesInWindow(floodWindow);
        if (msgCount >= floodMessages) {
            return FilterResult.block(getName(), "Message flood detected", points);
        }

        // 2. Duplicate / similarity check
        String normalized = TextNormalizer.normalize(message);
        List<String> recent = data.getRecentContent();
        int dupCount = 0;
        for (String prev : recent) {
            if (TextNormalizer.similarity(normalized, prev) >= similarityThreshold) {
                dupCount++;
            }
        }
        if (dupCount >= duplicateThreshold) {
            return FilterResult.block(getName(), "Repeated messages", points);
        }

        // 3. Caps check
        if (message.length() >= capsMinLength) {
            long caps = message.chars().filter(Character::isUpperCase).count();
            long letters = message.chars().filter(Character::isLetter).count();
            if (letters > 0 && (caps * 100 / letters) >= capsThreshold) {
                return FilterResult.block(getName(), "Excessive caps", capsPoints);
            }
        }

        // 4. Repeated characters (heeelllooo)
        if (repeatedChars && repeatedCharsPattern.matcher(message).matches()) {
            return FilterResult.block(getName(), "Repeated characters", capsPoints);
        }

        return FilterResult.allow();
    }

    @Override public String getName() { return "Spam"; }
    @Override public boolean isEnabled() { return enabled; }
}
