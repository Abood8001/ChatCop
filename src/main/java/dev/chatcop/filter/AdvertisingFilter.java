package dev.chatcop.filter;

import dev.chatcop.ChatCop;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.WordList;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AdvertisingFilter implements ChatFilter {

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private List<Pattern> patterns;
    private List<String> whitelist;

    public AdvertisingFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.advertising");
        if (s == null) { enabled = false; return; }
        enabled = s.getBoolean("enabled", true);
        points  = s.getInt("points", 10);
        whitelist = s.getStringList("whitelist-domains");

        patterns = new ArrayList<>(WordList.compiledAds);

        // Add custom domains from config
        for (String custom : s.getStringList("blocked-domains")) {
            try {
                patterns.add(Pattern.compile(custom, Pattern.CASE_INSENSITIVE));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        String lower = message.toLowerCase();

        // Check whitelist first
        for (String w : whitelist) {
            if (lower.contains(w.toLowerCase())) return FilterResult.allow();
        }

        for (Pattern p : patterns) {
            if (p.matcher(message).find()) {
                return FilterResult.block(getName(), "Advertising / external link", points);
            }
        }
        return FilterResult.allow();
    }

    @Override public String getName() { return "Advertising"; }
    @Override public boolean isEnabled() { return enabled; }
}
