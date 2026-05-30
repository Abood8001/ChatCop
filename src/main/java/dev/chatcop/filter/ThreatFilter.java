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

public class ThreatFilter implements ChatFilter {

    private List<String> whitelist;
    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private List<Pattern> patterns;

    public ThreatFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.threats");
        if (s == null) { enabled = false; return; }
        enabled  = s.getBoolean("enabled", true);
        points   = s.getInt("points", 12);
        patterns = new ArrayList<>(WordList.compiledThreats);
        for (String custom : plugin.getConfig().getStringList("filters.threats.blocked-phrases")) {
            try { patterns.add(Pattern.compile(custom, Pattern.CASE_INSENSITIVE)); } catch (Exception ignored) {}
        }
        whitelist = plugin.getConfig().getStringList("filters.threats.whitelisted-phrases");
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        String normalized = TextNormalizer.normalize(message);
        String lower = message.toLowerCase();
        for (String w : whitelist) {
            if (lower.contains(w.toLowerCase())) return FilterResult.allow();
        }
        for (Pattern p : patterns) {
            if (p.matcher(normalized).find()) {
                return FilterResult.block(getName(), "Threat detected", points);
            }
        }
        return FilterResult.allow();
    }

    @Override public String getName() { return "Threats"; }
    @Override public boolean isEnabled() { return enabled; }
}
