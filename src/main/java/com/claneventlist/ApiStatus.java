package com.claneventlist;

/**
 * Snapshot of Iron Forged API health + last events-feed metadata for UI.
 */
public final class ApiStatus
{
    public enum Level
    {
        UNKNOWN,
        HEALTHY,
        DEGRADED,
        DOWN
    }

    private final Level level;
    private final String tooltip;

    public ApiStatus(Level level, String tooltip)
    {
        this.level = level == null ? Level.UNKNOWN : level;
        this.tooltip = tooltip == null ? "No API status yet" : tooltip;
    }

    public Level getLevel()
    {
        return level;
    }

    public String getTooltip()
    {
        return tooltip;
    }

    public static ApiStatus unknown(String reason)
    {
        return new ApiStatus(Level.UNKNOWN, reason);
    }
}
