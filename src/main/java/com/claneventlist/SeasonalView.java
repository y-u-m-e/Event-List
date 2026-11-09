package com.claneventlist;

import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.List;

/** Response of {@code GET /plugins/seasonal/view}, parsed with LOWER_CASE_WITH_UNDERSCORES. */
public class SeasonalView
{
    public Event event;
    public Team team;
    public List<Stat> summary;
    public String message;
    public String webUrl;
    public List<JsonObject> pieces;

    public static class Event
    {
        public String id;
        public String name;
        public String status;
        public String startsAt;
        public String endsAt;
    }

    public static class Team
    {
        public int slot;
        public String name;
        public String color;
    }

    public static class Stat
    {
        public String label;
        public String value;
    }

    /** Accepts #RRGGBB and #RRGGBBAA; anything else gives the fallback. */
    public static Color parseColor(String hex, Color fallback)
    {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
        {
            return fallback;
        }
        int rgb = Integer.parseInt(hex.substring(1, 7), 16);
        int a = hex.length() == 9 ? Integer.parseInt(hex.substring(7), 16) : 255;
        return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, a);
    }
}
