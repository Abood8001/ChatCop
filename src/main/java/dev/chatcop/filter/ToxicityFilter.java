package dev.chatcop.filter;

import dev.chatcop.ChatCop;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.TextNormalizer;
import dev.chatcop.util.WordList;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ToxicityFilter implements ChatFilter {

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private boolean smartDetection;
    private List<Pattern> slurPatterns;
    private List<Pattern> customBlocked;
    private List<String> whitelisted;

    public ToxicityFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.toxicity");
        if (s == null) { enabled = false; return; }
        enabled         = s.getBoolean("enabled", true);
        points          = s.getInt("points", 8);
        smartDetection  = s.getBoolean("smart-detection", true);
        whitelisted     = s.getStringList("whitelisted-phrases");

        slurPatterns = s.getBoolean("block-slurs", true)
            ? new ArrayList<>(WordList.compiledSlurs)
            : new ArrayList<>();

        customBlocked = new ArrayList<>();
        for (String custom : s.getStringList("blocked-phrases")) {
            try {
                customBlocked.add(Pattern.compile(custom, Pattern.CASE_INSENSITIVE));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        // Smart detection: normalize first, then check
        String toCheck = smartDetection ? TextNormalizer.normalize(message) : message.toLowerCase();

        // Exempt only the whitelisted phrases themselves, not the whole message,
        // so a whitelisted word can't shield a slur elsewhere in the message.
        for (String w : whitelisted) {
            if (w == null || w.isBlank()) continue;
            toCheck = toCheck.replace(w.toLowerCase(), " ");
        }

        for (Pattern p : slurPatterns) {
            if (p.matcher(toCheck).find()) {
                return FilterResult.block(getName(), "Hate speech / slur detected", points);
            }
        }

        for (Pattern p : customBlocked) {
            if (p.matcher(toCheck).find()) {
                return FilterResult.block(getName(), "Prohibited phrase", points);
            }
        }

        return FilterResult.allow();
    }

    @Override public String getName() { return "Toxicity"; }
    @Override public boolean isEnabled() { return enabled; }
}
