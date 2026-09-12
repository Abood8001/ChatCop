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

public class AdvertisingFilter implements ChatFilter {

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private List<Pattern> patterns;
    private List<Pattern> customPatterns;
    private List<String> whitelist;

    public AdvertisingFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.advertising");
        if (s == null) { enabled = false; patterns = customPatterns = List.of(); whitelist = List.of(); return; }

        enabled = s.getBoolean("enabled", true);
        points  = s.getInt("points", 10);
        whitelist = FilterSupport.cleanList(s.getStringList("whitelist-domains"));

        patterns = new ArrayList<>();
        if (s.getBoolean("block-ips", true))  patterns.addAll(WordList.compiledAdIps);
        if (s.getBoolean("block-urls", true)) patterns.addAll(WordList.compiledAdUrls);

        // Custom domains are a separate list so the block-urls toggle can be
        // honoured for them too. Previously they ran even with block-urls off,
        // which made turning URL blocking off look broken.
        customPatterns = s.getBoolean("block-urls", true)
                ? FilterSupport.compileCustom(plugin, s.getStringList("blocked-domains"), "advertising")
                : List.of();
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        // Advertising runs on the raw text, not the normalized form: URLs are
        // made of the punctuation normalization strips. The only preprocessing
        // is undoing spelled-out dots, "example (dot) com" -> "example.com".
        String scan = TextNormalizer.deobfuscateDomains(message);

        // Blank whitelisted domains only, so a whitelisted domain can't be used
        // to smuggle a different link past the filter.
        for (String w : whitelist) {
            scan = replaceIgnoreCase(scan, w);
        }

        for (Pattern p : patterns) {
            if (p.matcher(scan).find()) {
                return FilterResult.block(getName(), "Advertising / external link", points);
            }
        }
        for (Pattern p : customPatterns) {
            if (p.matcher(scan).find()) {
                return FilterResult.block(getName(), "Blocked domain", points);
            }
        }
        return FilterResult.allow();
    }

    private String replaceIgnoreCase(String haystack, String needle) {
        if (needle.isEmpty()) return haystack;
        StringBuilder sb = new StringBuilder(haystack);
        String lower = haystack.toLowerCase();
        int idx = lower.indexOf(needle);
        while (idx >= 0) {
            for (int i = idx; i < idx + needle.length(); i++) sb.setCharAt(i, ' ');
            idx = lower.indexOf(needle, idx + needle.length());
        }
        return sb.toString();
    }

    @Override public String getName() { return "Advertising"; }
    @Override public boolean isEnabled() { return enabled; }
}
