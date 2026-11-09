package com.claneventlist;

import com.google.gson.Gson;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SeasonalViewServiceTest
{
    private static final String SAMPLE_JSON = "{\"ok\":true,"
        + "\"event\":{\"id\":\"delve\",\"name\":\"Delve Bingo\",\"status\":\"live\",\"starts_at\":\"2026-11-01T00:00:00Z\",\"ends_at\":\"2026-11-08T00:00:00Z\"},"
        + "\"team\":{\"slot\":1,\"name\":\"Team A\",\"color\":\"#10b981ff\"},"
        + "\"summary\":[{\"label\":\"Tiles\",\"value\":\"0 / 135\"}],"
        + "\"message\":null,\"web_url\":\"https://ironforged.gg/seasonal-event\","
        + "\"pieces\":[{\"type\":\"header\"},{\"type\":\"grid\",\"cells\":[{\"state\":\"open\"},{\"state\":\"locked\"}]}]}";

    @Test
    public void parsesTheContract()
    {
        SeasonalView v = SeasonalViewService.parse(new Gson(), SAMPLE_JSON);
        assertEquals("Delve Bingo", v.event.name);
        assertEquals("live", v.event.status);
        assertEquals("2026-11-08T00:00:00Z", v.event.endsAt);
        assertEquals("Team A", v.team.name);
        assertEquals("0 / 135", v.summary.get(0).value);
        assertEquals("https://ironforged.gg/seasonal-event", v.webUrl);
        assertEquals("grid", v.pieces.get(1).get("type").getAsString());
    }

    @Test
    public void malformedJsonIsNull()
    {
        assertNull(SeasonalViewService.parse(new Gson(), "{not json"));
        assertNull(SeasonalViewService.parse(new Gson(), ""));
    }

    @Test
    public void nullEventIsAView()
    {
        SeasonalView v = SeasonalViewService.parse(new Gson(), "{\"ok\":true,\"event\":null,\"pieces\":[]}");
        assertNotNull(v);
        assertNull(v.event);
    }

    @Test
    public void parsesColors()
    {
        assertEquals(new Color(0x10, 0xb9, 0x81, 0x4d), SeasonalView.parseColor("#10b9814d", Color.RED));
        assertEquals(new Color(0xff, 0, 0, 255), SeasonalView.parseColor("#ff0000", Color.BLUE));
        assertEquals(Color.BLUE, SeasonalView.parseColor("red", Color.BLUE));
        assertEquals(Color.BLUE, SeasonalView.parseColor(null, Color.BLUE));
    }
}
