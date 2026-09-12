package dev.chatcop.util;

import java.util.regex.Pattern;

public final class ColorUtil {

    private ColorUtil() {}

    private static final char SECTION = '§';
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{6}");
    private static final Pattern STRIP = Pattern.compile("(?i)" + SECTION + "(x(" + SECTION + "[0-9a-f]){6}|[0-9a-fk-or])");
    private static final Pattern STRIP_AMP = Pattern.compile("(?i)&(#[0-9a-f]{6}|[0-9a-fk-or])");

    /**
     * Translates &-codes and &#RRGGBB hex into section-sign codes.
     *
     * Only an ampersand followed by an actual colour code is converted. The old
     * implementation replaced every '&' in the string, so ordinary text like
     * "Fish & Chips" came out as "Fish <section>Chips" and swallowed the next
     * character. Write "&&" for a literal ampersand.
     */
    public static String translate(String input) {
        if (input == null) return "";

        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);

            if (c == '&' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);

                // "&&" is an escaped literal ampersand.
                if (next == '&') {
                    sb.append('&');
                    i += 2;
                    continue;
                }

                // "&#RRGGBB" hex colour.
                if (next == '#' && i + 7 < input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (HEX.matcher(hex).matches()) {
                        // Emitted lower-case so the same colour always produces
                        // the same string, whatever case the config used.
                        sb.append(SECTION).append('x');
                        for (char h : hex.toLowerCase().toCharArray()) sb.append(SECTION).append(h);
                        i += 8;
                        continue;
                    }
                }

                // "&a", "&l", "&r" ... only when it really is a code.
                if (CODES.indexOf(next) >= 0) {
                    sb.append(SECTION).append(Character.toLowerCase(next));
                    i += 2;
                    continue;
                }
            }

            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Removes both section-sign and ampersand colour codes. */
    public static String strip(String input) {
        if (input == null) return "";
        return STRIP_AMP.matcher(STRIP.matcher(input).replaceAll("")).replaceAll("");
    }
}
