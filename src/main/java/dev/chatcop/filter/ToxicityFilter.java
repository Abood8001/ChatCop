package dev.chatcop.filter;

import dev.chatcop.ChatCop;
import dev.chatcop.model.FilterResult;
import dev.chatcop.model.PlayerData;
import dev.chatcop.util.TextNormalizer;
import dev.chatcop.util.WordList;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Pattern;

public class ToxicityFilter implements ChatFilter {

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private boolean smartDetection;
    private List<Pattern> slurPatterns;
    private List<Pattern> sexualPatterns;
    private List<Pattern> customBlocked;
    private List<String> whitelisted;

    public ToxicityFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.toxicity");
        if (s == null) {
            enabled = false;
            slurPatterns = sexualPatterns = customBlocked = List.of();
            whitelisted = List.of();
            return;
        }

        enabled        = s.getBoolean("enabled", true);
        points         = s.getInt("points", 8);
        smartDetection = s.getBoolean("smart-detection", true);
        whitelisted    = FilterSupport.cleanList(s.getStringList("whitelisted-phrases"));

        slurPatterns   = s.getBoolean("block-slurs", true) ? WordList.compiledSlurs : List.of();
        sexualPatterns = s.getBoolean("block-sexual-content", true) ? WordList.compiledSexual : List.of();
        customBlocked  = FilterSupport.compileCustom(plugin, s.getStringList("blocked-phrases"), "toxicity");
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        List<String> variants = smartDetection
                ? TextNormalizer.variants(message)
                : List.of(message.toLowerCase());
        variants = FilterSupport.applyWhitelist(variants, whitelisted);

        if (TextNormalizer.matchesAny(variants, slurPatterns)) {
            return FilterResult.block(getName(), "Hate speech / slur detected", points);
        }
        // Reported separately from slurs: different offence, and staff reading
        // an alert should not be told a sexual-content hit was a racial slur.
        if (TextNormalizer.matchesAny(variants, sexualPatterns)) {
            return FilterResult.block(getName(), "Sexual content", points);
        }
        if (TextNormalizer.matchesAny(variants, customBlocked)) {
            return FilterResult.block(getName(), "Prohibited phrase", points);
        }
        return FilterResult.allow();
    }

    @Override public String getName() { return "Toxicity"; }
    @Override public boolean isEnabled() { return enabled; }
}
