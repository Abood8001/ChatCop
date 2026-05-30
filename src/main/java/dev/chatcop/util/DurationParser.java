package dev.chatcop.util;

public final class DurationParser {

    private DurationParser() {}

    /**
     * Parse strings like "10m", "1h30m", "2d", "permanent" into milliseconds.
     * Returns -1 for permanent.
     */
    public static long parse(String input) {
        if (input == null) return -1;
        input = input.trim().toLowerCase();
        if (input.equals("perm") || input.equals("permanent") || input.equals("-1")) return -1;

        long total = 0;
        int i = 0;
        while (i < input.length()) {
            int numStart = i;
            while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
            if (i == numStart) break;
            long num = Long.parseLong(input.substring(numStart, i));
            if (i >= input.length()) break;
            char unit = input.charAt(i++);
            total += switch (unit) {
                case 's' -> num * 1000L;
                case 'm' -> num * 60_000L;
                case 'h' -> num * 3_600_000L;
                case 'd' -> num * 86_400_000L;
                case 'w' -> num * 604_800_000L;
                default  -> 0L;
            };
        }
        return total <= 0 ? -1 : total;
    }

    public static String format(long ms) {
        if (ms < 0) return "Permanent";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }
}
