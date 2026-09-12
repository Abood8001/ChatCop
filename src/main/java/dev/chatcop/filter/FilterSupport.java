package dev.chatcop.filter;

import dev.chatcop.ChatCop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared helpers for turning config lists into usable matchers.
 *
 * Centralised because every filter previously did this slightly differently,
 * and only one of them guarded against blank entries — a blank phrase compiles
 * to a pattern that matches every message, which silently blocked all chat.
 */
public final class FilterSupport {

    private FilterSupport() {}

    /** Anything that would make the string meaningful as a regex rather than a literal. */
    private static final Pattern REGEX_METACHARS = Pattern.compile("[\\\\\\[\\]{}()*+?^$|.]");

    /**
     * Compiles user-supplied phrases.
     *
     * A phrase with no regex metacharacters is treated as a literal word and
     * wrapped in word boundaries, so "nega" stops matching "negative". Anything
     * containing metacharacters is compiled as the regex the author intended.
     * Blank entries are skipped, and a broken regex is reported instead of
     * being swallowed, so a typo doesn't silently disable a rule.
     */
    public static List<Pattern> compileCustom(ChatCop plugin, List<String> raw, String filterName) {
        List<Pattern> out = new ArrayList<>();
        if (raw == null) return out;

        for (String phrase : raw) {
            if (phrase == null || phrase.isBlank()) continue;
            String trimmed = phrase.trim();
            try {
                String expr = REGEX_METACHARS.matcher(trimmed).find()
                        ? trimmed
                        : "\\b" + Pattern.quote(trimmed) + "\\b";
                Pattern compiled = Pattern.compile(expr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

                // A pattern that matches the empty string matches everything.
                if (compiled.matcher("").find()) {
                    warn(plugin, "[" + filterName + "] Ignoring phrase \"" + trimmed
                            + "\": it matches every message.");
                    continue;
                }
                out.add(compiled);
            } catch (PatternSyntaxException e) {
                warn(plugin, "[" + filterName + "] Ignoring invalid pattern \"" + trimmed
                        + "\": " + e.getDescription());
            }
        }
        return out;
    }

    /** Plugin is null in unit tests, where there is no logger to talk to. */
    private static void warn(ChatCop plugin, String message) {
        if (plugin != null) plugin.getLogger().warning(message);
    }

    /** Lowercased, blank-free copy of a config string list. */
    public static List<String> cleanList(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            out.add(s.trim().toLowerCase());
        }
        return out;
    }

    /**
     * Blanks whitelisted phrases out of each variant.
     *
     * Only the phrase itself is exempted, never the whole message, so a
     * whitelisted word can't be used as a free pass for a slur beside it.
     */
    public static List<String> applyWhitelist(List<String> variants, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) return variants;

        List<String> out = new ArrayList<>(variants.size());
        for (String v : variants) {
            String cleaned = v;
            for (String w : whitelist) {
                cleaned = cleaned.replace(w, " ");
            }
            out.add(cleaned);
        }
        return out;
    }
}
