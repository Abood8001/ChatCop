package dev.chatcop.util;

public final class TextNormalizer {

    private TextNormalizer() {}

    /**
     * Normalize text for smart detection:
     * - Lowercase
     * - Replace leet speak characters with their letter equivalents
     * - Collapse repeated characters (heeellooo -> hello)
     * - Remove zero-width characters and unusual unicode
     * - Strip spaces between individual letters (h e l l o -> hello)
     */
    public static String normalize(String input) {
        if (input == null) return "";

        // Remove zero-width / invisible chars
        input = input.replaceAll("[\\u200B-\\u200D\\uFEFF\\u00AD]", "");

        // Lowercase
        input = input.toLowerCase();

        // Strip symbol separators: #n#i#g, n.i.g, n-i-g, n*i*g etc.
        input = input.replaceAll("(?<=[a-z0-9])[^a-z0-9\\s]+(?=[a-z0-9])", "");

        // Leet speak substitution

        // Leet speak substitution
        input = input
            .replace("@", "a")
            .replace("4", "a")
            .replace("3", "e")
            .replace("1", "i")
            .replace("!", "i")
            .replace("|", "i")
            .replace("0", "o")
            .replace("5", "s")
            .replace("$", "s")
            .replace("7", "t")
            .replace("+", "t")
            .replace("8", "b")
            .replace("6", "g")
            .replace("9", "g")
            .replace("(", "c")
            .replace("<", "c");

        // Collapse repeated characters (3+ of same -> 2 max, to preserve intentional doubles)
        input = input.replaceAll("(.)\\1{2,}", "$1$1");

        // Strip spaces between single characters that form a word (e.g. "f u c k" -> "fuck")
        // Only collapse if the pattern is consistent (alternating letter-space)
        input = collapseSpacedWord(input);

        return input;
    }

    private static String collapseSpacedWord(String input) {
        // Match sequences like "f u c k" or "n i g g e r"
        return input.replaceAll("(?<=\\b)(\\w)(?:\\s+\\1?\\s*)(\\w)(?=\\b|\\s\\w)", "$1$2")
                    .replaceAll("(?<=[a-z]) (?=[a-z] )", ""); // best-effort strip
    }

    /**
     * Calculate Levenshtein similarity ratio between two strings (0.0 - 1.0).
     */
    public static double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshtein(a, b) / maxLen;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] curr = new int[b.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = curr;
        }
        return prev[b.length()];
    }
}
