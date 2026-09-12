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
    private long floodWindowMs;
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

        enabled             = s.getBoolean("enabled", true);
        points              = s.getInt("points", 4);
        duplicateThreshold  = Math.max(1, s.getInt("duplicate-threshold", 4));
        floodMessages       = Math.max(2, s.getInt("flood-messages", 6));
        floodWindowMs       = Math.max(1, s.getLong("flood-window", 3)) * 1000L;
        similarityThreshold = s.getDouble("similarity-threshold", 0.80);
        capsThreshold       = s.getInt("caps-threshold", 70);
        capsMinLength       = s.getInt("caps-min-length", 12);
        capsPoints          = s.getInt("caps-points", 2);
        repeatedChars       = s.getBoolean("repeated-chars", true);

        int repeatThreshold = Math.max(2, s.getInt("repeated-chars-threshold", 10));
        repeatedCharsPattern = Pattern.compile(".*?(.)\\1{" + repeatThreshold + ",}.*", Pattern.DOTALL);
    }

    public long getFloodWindowMs() { return floodWindowMs; }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        // 1. Flood
        if (data.getMessagesInWindow(floodWindowMs) >= floodMessages) {
            return FilterResult.block(getName(), "Message flood detected", points);
        }

        // 2. Duplicate / similarity.
        // Both sides are normalized the same way. Previously the incoming
        // message was normalized but the stored history was only lowercased,
        // so anything with caps, digits or punctuation was compared against a
        // differently-shaped string and scored far lower than it should have.
        String normalized = TextNormalizer.normalize(message);
        List<String> recent = data.getRecentContent();
        int dupCount = 0;
        for (String prev : recent) {
            if (TextNormalizer.similarity(normalized, prev) >= similarityThreshold) dupCount++;
        }
        if (dupCount >= duplicateThreshold) {
            return FilterResult.block(getName(), "Repeated messages", points);
        }

        // 3. Caps
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
