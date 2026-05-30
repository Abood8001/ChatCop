package dev.chatcop.util;

public final class ColorUtil {

    private ColorUtil() {}

    public static String translate(String input) {
        if (input == null) return "";
        input = translateHex(input);
        input = input.replace("&", "\u00a7").replace("\u00a7\u00a7", "&");
        return input;
    }

    private static String translateHex(String input) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            if (i + 7 < input.length() && input.charAt(i) == '&' && input.charAt(i + 1) == '#') {
                String hex = input.substring(i + 2, i + 8);
                if (hex.matches("[0-9a-fA-F]{6}")) {
                    sb.append('\u00a7').append('x');
                    for (char c : hex.toCharArray()) {
                        sb.append('\u00a7').append(c);
                    }
                    i += 8;
                    continue;
                }
            }
            sb.append(input.charAt(i));
            i++;
        }
        return sb.toString();
    }

    public static String strip(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)(&|§)([0-9a-fk-or]|#[0-9a-fA-F]{6}|x(§[0-9a-fA-F]){6})", "");
    }
}