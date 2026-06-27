package dev.chatcop.model;

public class FilterResult {

    public enum Action { ALLOW, CENSOR, BLOCK, SHADOW }

    private final Action action;
    private final String filterName;
    private final String reason;
    private final String censored;
    private final int points;

    private FilterResult(Action action, String filterName, String reason, String censored, int points) {
        this.action = action;
        this.filterName = filterName;
        this.reason = reason;
        this.censored = censored;
        this.points = points;
    }

    public static FilterResult allow() {
        return new FilterResult(Action.ALLOW, null, null, null, 0);
    }

    public static FilterResult block(String filterName, String reason, int points) {
        return new FilterResult(Action.BLOCK, filterName, reason, null, points);
    }

    public static FilterResult shadow(String filterName, String reason, int points) {
        return new FilterResult(Action.SHADOW, filterName, reason, null, points);
    }

    public static FilterResult censor(String filterName, String censored, int points) {
        return new FilterResult(Action.CENSOR, filterName, "Profanity censored", censored, points);
    }

    public Action getAction() { return action; }
    public String getFilterName() { return filterName; }
    public String getReason() { return reason; }
    public String getCensored() { return censored; }
    public int getPoints() { return points; }
    public boolean isClean() { return action == Action.ALLOW; }
}
