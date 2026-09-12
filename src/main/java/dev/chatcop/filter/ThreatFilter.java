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

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private List<Pattern> patterns;
    private List<String> whitelist;

    public ThreatFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.threats");
        if (s == null) { enabled = false; patterns = List.of(); whitelist = List.of(); return; }

        enabled  = s.getBoolean("enabled", true);
        points   = s.getInt("points", 12);

        patterns = new ArrayList<>(WordList.compiledThreats);
        patterns.addAll(FilterSupport.compileCustom(plugin, s.getStringList("blocked-phrases"), "threats"));
        whitelist = FilterSupport.cleanList(s.getStringList("whitelisted-phrases"));
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        List<String> variants = FilterSupport.applyWhitelist(TextNormalizer.variants(message), whitelist);

        if (TextNormalizer.matchesAny(variants, patterns)) {
            return FilterResult.block(getName(), "Threat detected", points);
        }
        return FilterResult.allow();
    }

    @Override public String getName() { return "Threats"; }
    @Override public boolean isEnabled() { return enabled; }
}
