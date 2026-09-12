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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfanityFilter implements ChatFilter {

    private static final Pattern TOKEN = Pattern.compile("\\S+");

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private int censorPoints;
    private boolean censorMode;
    private char censorChar;
    private int maxPerMessage;
    private List<String> whitelist;
    private List<Pattern> patterns;

    public ProfanityFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.profanity");
        if (s == null) { enabled = false; patterns = List.of(); whitelist = List.of(); return; }

        enabled       = s.getBoolean("enabled", true);
        points        = s.getInt("points", 3);
        censorPoints  = s.getInt("censor-points", Math.max(1, points / 2));
        censorMode    = s.getBoolean("censor-mode", true);
        String cChar  = s.getString("censor-char", "*");
        censorChar    = (cChar == null || cChar.isEmpty()) ? '*' : cChar.charAt(0);
        maxPerMessage = s.getInt("max-per-message", 3);

        // Per-instance list; never mutate the shared static one.
        patterns = new ArrayList<>(WordList.compiledProfanity);
        patterns.addAll(FilterSupport.compileCustom(plugin, s.getStringList("blocked-phrases"), "profanity"));
        whitelist = FilterSupport.cleanList(s.getStringList("whitelisted-phrases"));
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        List<String> variants = FilterSupport.applyWhitelist(TextNormalizer.variants(message), whitelist);

        int count = worstCount(variants);
        if (count == 0) return FilterResult.allow();

        if (count > maxPerMessage) {
            return FilterResult.block(getName(), "Excessive profanity", points);
        }
        if (!censorMode) {
            return FilterResult.block(getName(), "Profanity", points);
        }

        String censored = censorTokens(message);

        // Only report a censor if the censored text is genuinely clean. Detection
        // runs on the normalized forms but censoring has to edit the original, so
        // spaced-out obfuscation ("f u c k") can be detected without being safely
        // replaceable. Blocking beats telling the player we censored a message we
        // then delivered word for word.
        if (worstCount(FilterSupport.applyWhitelist(TextNormalizer.variants(censored), whitelist)) > 0) {
            return FilterResult.block(getName(), "Profanity", points);
        }

        return FilterResult.censor(getName(), censored, censorPoints);
    }

    /** Highest distinct-hit count across the variants (max, not sum — same word, different spellings). */
    private int worstCount(List<String> variants) {
        int worst = 0;
        for (String v : variants) worst = Math.max(worst, countDistinct(v));
        return worst;
    }

    /**
     * Counts distinct profane regions rather than raw pattern hits.
     *
     * "asshole" is matched by two patterns at once, which used to count as two
     * words and pushed short messages over max-per-message far too early.
     * Overlapping matches are merged so it counts as the one word it is.
     */
    private int countDistinct(String text) {
        if (text.isEmpty()) return 0;

        List<int[]> spans = new ArrayList<>();
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                if (m.end() > m.start()) spans.add(new int[]{m.start(), m.end()});
            }
        }
        if (spans.isEmpty()) return 0;

        spans.sort((x, y) -> Integer.compare(x[0], y[0]));
        int count = 1;
        int end = spans.get(0)[1];
        for (int i = 1; i < spans.size(); i++) {
            int[] span = spans.get(i);
            if (span[0] >= end) {
                count++;
                end = span[1];
            } else {
                end = Math.max(end, span[1]);
            }
        }
        return count;
    }

    /**
     * Censors whole tokens, in place, so obfuscated spellings are handled too:
     * "f.u.c.k" normalizes to a hit, and the token it came from is what gets
     * starred. Replacement is character-for-character, so offsets stay valid.
     */
    private String censorTokens(String message) {
        StringBuilder out = new StringBuilder(message);
        Matcher tok = TOKEN.matcher(message);

        while (tok.find()) {
            String token = tok.group();
            List<String> variants = FilterSupport.applyWhitelist(TextNormalizer.variants(token), whitelist);
            if (worstCount(variants) > 0) {
                for (int i = tok.start(); i < tok.end(); i++) out.setCharAt(i, censorChar);
            }
        }
        return out.toString();
    }

    @Override public String getName() { return "Profanity"; }
    @Override public boolean isEnabled() { return enabled; }
}
