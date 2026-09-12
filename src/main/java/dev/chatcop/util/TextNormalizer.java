package dev.chatcop.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns a raw chat message into one or more comparable forms for matching.
 *
 * Order matters here. Leet substitution runs BEFORE separator stripping,
 * because stripping first deletes the very characters the word lists key on:
 * "sh!t" would become "sht" (no match) instead of "shit", and "n!gg3r" would
 * become "ngger" instead of "nigger". The censor star is deliberately kept
 * through both stages so patterns like f[u*]+ck can still see "f*ck".
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    /** Zero-width, soft hyphen, format and private-use characters. */
    private static final Pattern INVISIBLE =
            Pattern.compile("[\\p{Cf}\\p{Co}\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]");

    /** Symbol separators wedged between letters: n.i.g, f-u-c-k, w_o_r_d. Keeps '*'. */
    private static final Pattern SEPARATORS =
            Pattern.compile("(?<=[a-z0-9*])[^a-z0-9\\s*]+(?=[a-z0-9*])");

    /** Three or more of the same character collapse to two, preserving real doubles. */
    private static final Pattern REPEATS = Pattern.compile("(.)\\1{2,}");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** "example(dot)com", "example dot com" -> "example.com" */
    private static final Pattern SPELLED_DOT =
            Pattern.compile("(?i)\\s*[(\\[{]?\\s*(?:dot|d0t)\\s*[)\\]}]?\\s*");

    /** "name (at) host" -> "name@host" */
    private static final Pattern SPELLED_AT =
            Pattern.compile("(?i)\\s*[(\\[{]\\s*(?:at)\\s*[)\\]}]\\s*");

    private static final char[] LEET_FROM = {'@', '4', '3', '1', '!', '|', '0', '5', '$', '7', '+', '8', '6', '9', '(', '<'};
    private static final char[] LEET_TO   = {'a', 'a', 'e', 'i', 'i', 'i', 'o', 's', 's', 't', 't', 'b', 'g', 'g', 'c', 'c'};

    /**
     * Canonical normalized form, with leet substitution applied. Used wherever
     * a single comparable form is needed (spam duplicate detection).
     */
    public static String normalize(String input) {
        return base(input, true);
    }

    /**
     * Same pipeline without leet substitution.
     *
     * Needed because leet mapping is lossy at word edges: '!' maps to 'i', so
     * "fuck!" would become "fucki" and stop matching \bfuck\b. Keeping a
     * non-leet form alongside means trailing punctuation never hides a word.
     */
    public static String plain(String input) {
        return base(input, false);
    }

    private static String base(String input, boolean applyLeet) {
        if (input == null || input.isEmpty()) return "";
        String s = INVISIBLE.matcher(input).replaceAll("");
        s = s.toLowerCase();
        if (applyLeet) s = leet(s);
        s = SEPARATORS.matcher(s).replaceAll("");
        s = REPEATS.matcher(s).replaceAll("$1$1");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /**
     * Every form a filter should test a message against: the plain and the
     * leet-substituted normalization, plus a letter-spacing-joined version of
     * each ("f u c k" -> "fuck"). Nothing is ever replaced, only added, so a
     * variant can add detections but never lose one.
     */
    public static List<String> variants(String input) {
        List<String> out = new ArrayList<>(4);
        for (String form : new String[]{plain(input), normalize(input)}) {
            if (!form.isEmpty() && !out.contains(form)) out.add(form);
            String joined = joinShortTokenRuns(form);
            if (!joined.isEmpty() && !out.contains(joined)) out.add(joined);
        }
        return out;
    }

    /** True if any of the message's variants matches any of the patterns. */
    public static boolean matchesAny(List<String> variants, List<Pattern> patterns) {
        for (String v : variants) {
            for (Pattern p : patterns) {
                if (p.matcher(v).find()) return true;
            }
        }
        return false;
    }

    /**
     * Joins runs of consecutive very short tokens, which is what letter-spacing
     * looks like once it is tokenized: "f u c k" -> "fuck", "fu ck" -> "fuck".
     *
     * A run needs at least two tokens and at least three characters total, so
     * ordinary chat ("is a", "u ok") is left alone.
     */
    private static String joinShortTokenRuns(String s) {
        if (s.indexOf(' ') < 0) return s;

        String[] tokens = s.split(" ");
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;

        while (i < tokens.length) {
            if (tokens[i].length() <= 2) {
                int j = i;
                StringBuilder run = new StringBuilder();
                while (j < tokens.length && tokens[j].length() <= 2) {
                    run.append(tokens[j]);
                    j++;
                }
                if (j - i >= 2 && run.length() >= 3) {
                    if (out.length() > 0) out.append(' ');
                    out.append(run);
                    i = j;
                    continue;
                }
            }
            if (out.length() > 0) out.append(' ');
            out.append(tokens[i]);
            i++;
        }
        return out.toString();
    }

    /**
     * Undoes the spelled-out obfuscations advertisers use, so the advertising
     * filter can run its patterns over "play (dot) example (dot) com" too.
     * Applied to the raw message, since URL patterns need the real punctuation.
     */
    public static String deobfuscateDomains(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = INVISIBLE.matcher(input).replaceAll("");
        s = SPELLED_DOT.matcher(s).replaceAll(".");
        s = SPELLED_AT.matcher(s).replaceAll("@");
        return s;
    }

    private static String leet(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        outer:
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            for (int k = 0; k < LEET_FROM.length; k++) {
                if (c == LEET_FROM[k]) {
                    sb.append(LEET_TO[k]);
                    continue outer;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Levenshtein similarity ratio between two strings (0.0 - 1.0).
     * Bails out early when the lengths are too far apart to ever reach the
     * caller's threshold, which keeps the spam filter cheap on long messages.
     */
    public static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;

        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;

        // Length alone caps the achievable ratio; skip the O(n*m) work if it can't matter.
        int minLen = Math.min(a.length(), b.length());
        if ((double) minLen / maxLen < 0.5) return (double) minLen / maxLen;

        return 1.0 - (double) levenshtein(a, b) / maxLen;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }
}
