package com.claneventlist;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup(EventListConfig.CONFIG_GROUP)
public interface EventListConfig extends Config
{
    String CONFIG_GROUP = "claneventlist";

    @ConfigSection(
        name = "Google Sheet",
        description = "Configure Google Sheet data source",
        position = 0
    )
    String sheetSection = "sheetSection";

    @ConfigSection(
        name = "Overlay",
        description = "Configure overlay appearance",
        position = 1
    )
    String overlaySection = "overlaySection";

    @ConfigSection(
        name = "Panel",
        description = "Configure panel settings",
        position = 2
    )
    String panelSection = "panelSection";

    @ConfigSection(
        name = "Seasonal Reporter",
        description = "Submit seasonal boss drops to Iron Forged backend",
        position = 3
    )
    String seasonalSection = "seasonalSection";

    // ========== Google Sheet Settings ==========

    @ConfigItem(
        keyName = "sheetId",
        name = "Google Sheet ID",
        description = "The ID from your Google Sheet URL (the long string between /d/ and /edit)",
        section = sheetSection,
        position = 0
    )
    default String sheetId()
    {
        return "";
    }

    @ConfigItem(
        keyName = "sheetName",
        name = "Sheet Tab Name",
        description = "The name of the specific sheet/tab to read from (leave empty for first sheet)",
        section = sheetSection,
        position = 1
    )
    default String sheetName()
    {
        return "";
    }

    @ConfigItem(
        keyName = "refreshInterval",
        name = "Refresh Interval (min)",
        description = "How often to refresh event data from the sheet (in minutes)",
        section = sheetSection,
        position = 2
    )
    @Range(min = 1, max = 60)
    default int refreshInterval()
    {
        return 60;
    }

    // ========== Overlay Settings ==========

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay",
        description = "Display the next event overlay on the game screen",
        section = overlaySection,
        position = 0
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "overlayBackgroundColor",
        name = "Background Color",
        description = "Background color of the overlay",
        section = overlaySection,
        position = 1
    )
    default Color overlayBackgroundColor()
    {
        return new Color(30, 30, 30, 200);
    }

    @ConfigItem(
        keyName = "overlayTextColor",
        name = "Text Color",
        description = "Text color for the overlay",
        section = overlaySection,
        position = 2
    )
    default Color overlayTextColor()
    {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "overlayAccentColor",
        name = "Accent Color",
        description = "Accent color for event name and highlights",
        section = overlaySection,
        position = 3
    )
    default Color overlayAccentColor()
    {
        return new Color(255, 200, 0);
    }

    @ConfigItem(
        keyName = "imminentColor",
        name = "Imminent Event Color",
        description = "Color when event is within 1 hour",
        section = overlaySection,
        position = 4
    )
    default Color imminentColor()
    {
        return new Color(255, 100, 100);
    }

    @ConfigItem(
        keyName = "activeColor",
        name = "Active Event Color",
        description = "Color for currently happening events",
        section = overlaySection,
        position = 5
    )
    default Color activeColor()
    {
        return new Color(100, 255, 100);
    }

    @ConfigItem(
        keyName = "simpleOverlay",
        name = "Simple Overlay",
        description = "Use compact overlay (just event name and time, no titles)",
        section = overlaySection,
        position = 6
    )
    default boolean simpleOverlay()
    {
        return false;
    }

    // ========== Panel Settings ==========

    @ConfigItem(
        keyName = "maxEventsShown",
        name = "Max Events Shown",
        description = "Maximum number of events to show in the panel (scroll for more)",
        section = panelSection,
        position = 0
    )
    @Range(min = 1, max = 50)
    default int maxEventsInPanel()
    {
        return 10;
    }

    @ConfigItem(
        keyName = "showPastEvents",
        name = "Show Past Events",
        description = "Include events that have already passed",
        section = panelSection,
        position = 1
    )
    default boolean showPastEvents()
    {
        return false;
    }

    // ========== Seasonal Reporter ==========

    @ConfigItem(
        keyName = "seasonalEnabled",
        name = "Enable Seasonal Reporter",
        description = "Allow this plugin to send eligible seasonal drops to Iron Forged backend",
        section = seasonalSection,
        position = 0,
        warning = "This feature sends loot metadata (boss, item id, quantity, timestamp) to an external Iron Forged API."
    )
    default boolean seasonalEnabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "seasonalDryRun",
        name = "Dry Run Mode",
        description = "Capture seasonal drops and log them without sending API requests",
        section = seasonalSection,
        position = 1
    )
    default boolean seasonalDryRun()
    {
        return true;
    }

    @ConfigItem(
        keyName = "seasonalEventId",
        name = "Seasonal Event ID",
        description = "Event identifier configured on the website (for example: seasonal-2026-q2)",
        section = seasonalSection,
        position = 2
    )
    default String seasonalEventId()
    {
        return "";
    }

    @ConfigItem(
        keyName = "seasonalConnectCode",
        name = "Ingest Key",
        description = "Manual ingest key provided by admin",
        section = seasonalSection,
        position = 3
    )
    default String seasonalConnectCode()
    {
        return "";
    }

    @ConfigItem(
        keyName = "seasonalLinkNow",
        name = "Validate Settings Now",
        description = "Toggle on to validate event id + ingest key are configured",
        section = seasonalSection,
        position = 4
    )
    default boolean seasonalLinkNow()
    {
        return false;
    }

    @ConfigItem(
        keyName = "seasonalTestApiNow",
        name = "Test API Now",
        description = "Toggle on to validate backend connectivity and identity",
        section = seasonalSection,
        position = 5
    )
    default boolean seasonalTestApiNow()
    {
        return false;
    }

    @ConfigItem(
        keyName = "seasonalFlushQueueNow",
        name = "Flush Queue Now",
        description = "Toggle on to immediately attempt sending all queued submissions",
        section = seasonalSection,
        position = 6
    )
    default boolean seasonalFlushQueueNow()
    {
        return false;
    }
}

