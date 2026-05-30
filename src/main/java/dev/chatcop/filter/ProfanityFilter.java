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

    private final ChatCop plugin;
    private boolean enabled;
    private int points;
    private boolean censorMode;
    private char censorChar;
    private int maxPerMessage;
    private List<String> whitelist;

    public ProfanityFilter(ChatCop plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("filters.profanity");
        if (s == null) { enabled = false; return; }
        enabled        = s.getBoolean("enabled", true);
        points         = s.getInt("points", 3);
        censorMode     = s.getBoolean("censor-mode", true);
        String cChar   = s.getString("censor-char", "*");
        censorChar     = cChar.isEmpty() ? '*' : cChar.charAt(0);
        maxPerMessage  = s.getInt("max-per-message", 3);
        for (String custom : plugin.getConfig().getStringList("filters.profanity.blocked-phrases")) {
            try { WordList.compiledProfanity = new ArrayList<>(WordList.compiledProfanity);
                WordList.compiledProfanity.add(Pattern.compile(custom, Pattern.CASE_INSENSITIVE));
            } catch (Exception ignored) {}
        }
        whitelist = plugin.getConfig().getStringList("filters.profanity.whitelisted-phrases");
    }

    @Override
    public FilterResult analyze(Player player, String message, PlayerData data) {
        List<Pattern> profanity = WordList.compiledProfanity;
        String normalized = TextNormalizer.normalize(message);

        int count = 0;
        String lower = message.toLowerCase();
        for (String w : whitelist) {
            if (lower.contains(w.toLowerCase())) return FilterResult.allow();
        }
        for (Pattern p : profanity) {
            Matcher m = p.matcher(normalized);
            while (m.find()) count++;
        }

        if (count == 0) return FilterResult.allow();

        if (count > maxPerMessage) {
            return FilterResult.block(getName(), "Excessive profanity", points);
        }

        if (censorMode) {
            String censored = censorMessage(message);
            return FilterResult.censor(getName(), censored, points / 2);
        }

        return FilterResult.block(getName(), "Profanity", points);
    }

    private String censorMessage(String message) {
        String result = message;
        // Apply censorship on original message (case-insensitive replacements)
        for (Pattern p : WordList.compiledProfanity) {
            Matcher m = p.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String stars = String.valueOf(censorChar).repeat(m.group().length());
                m.appendReplacement(sb, stars);
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    @Override public String getName() { return "Profanity"; }
    @Override public boolean isEnabled() { return enabled; }
}
