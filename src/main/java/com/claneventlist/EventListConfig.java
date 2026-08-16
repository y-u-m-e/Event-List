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
    String CONFIG_GROUP = "ironforgedclanevents";

    @ConfigSection(
        name = "Events",
        description = "Load the Iron Forged clan event calendar",
        position = 0
    )
    String eventsSection = "eventsSection";

    @ConfigSection(
        name = "Seasonal Drop Submit",
        description = "Opt-in auto-submit of notable seasonal drops",
        position = 1
    )
    String seasonalSubmitSection = "seasonalSubmitSection";

    @ConfigSection(
        name = "Seasonal Event",
        description = "Seasonal Event ID and Passphrase (overlay + drop submit identity)",
        position = 2
    )
    String seasonalEventPassphraseSection = "seasonalEventPassphraseSection";

    @ConfigSection(
        name = "Panel",
        description = "Configure panel settings",
        position = 3
    )
    String panelSection = "panelSection";

    @ConfigSection(
        name = "Teams",
        description = "Colored team badges in chat, friends/clan lists, and overhead names",
        position = 4
    )
    String teamsSection = "teamsSection";

    // ========== Events ==========

    @ConfigItem(
        keyName = "enableEventsFeed",
        name = "Load clan events",
        description = "Fetch the Iron Forged events calendar from api.ironforged.gg over the internet",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        section = eventsSection,
        position = 0
    )
    default boolean enableEventsFeed()
    {
        return false;
    }

    @ConfigItem(
        keyName = "refreshInterval",
        name = "Refresh Interval (min)",
        description = "How often to refresh event data (in minutes)",
        section = eventsSection,
        position = 1
    )
    @Range(min = 1, max = 60)
    default int refreshInterval()
    {
        return 15;
    }

    @ConfigItem(
        keyName = "useDevApi",
        name = "Use dev API",
        description = "Use https://dev-api.ironforged.gg instead of production https://api.ironforged.gg for events, health, seasonal catalog, drop submit, and team roster sync",
        section = eventsSection,
        position = 2
    )
    default boolean useDevApi()
    {
        return false;
    }

    // ========== Seasonal Drop Submit ==========

    @ConfigItem(
        keyName = "enableSeasonalSubmit",
        name = "Submit notable seasonal drops",
        description = "When enabled, only catalog-listed boss/item drops are screenshotted and sent with Seasonal Event ID + Seasonal Event Passphrase. Common loot is ignored locally and never uploaded.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        section = seasonalSubmitSection,
        position = 0
    )
    default boolean enableSeasonalSubmit()
    {
        return false;
    }

    @ConfigItem(
        keyName = "seasonalCatalogRefreshInterval",
        name = "Catalog refresh (min)",
        description = "How often to refresh the notable-drop catalog from the API",
        section = seasonalSubmitSection,
        position = 1
    )
    @Range(min = 1, max = 60)
    default int seasonalCatalogRefreshInterval()
    {
        return 15;
    }

    @ConfigItem(
        keyName = "seasonalSubmitDryRun",
        name = "Dry run (no upload)",
        description = "Detect and match notable drops, but do not screenshot or POST them",
        section = seasonalSubmitSection,
        position = 2
    )
    default boolean seasonalSubmitDryRun()
    {
        return false;
    }

    // ========== Seasonal Event ==========

    @ConfigItem(
        keyName = "showSeasonalEventPassphrase",
        name = "Show Seasonal Event Passphrase",
        description = "Draw the Seasonal Event Passphrase on the game screen (drag to reposition)",
        section = seasonalEventPassphraseSection,
        position = 0
    )
    default boolean showSeasonalEventPassphrase()
    {
        return false;
    }

    @ConfigItem(
        keyName = "seasonalEventId",
        name = "Seasonal Event ID",
        description = "Unique id for which seasonal event drops count toward. Used when the API does not publish one, or to override it.",
        section = seasonalEventPassphraseSection,
        position = 1
    )
    default String seasonalEventId()
    {
        return "";
    }

    @ConfigItem(
        keyName = "seasonalEventPassphrase",
        name = "Seasonal Event Passphrase",
        description = "Shown on the overlay and used to authenticate drop submits. Prefer the value from the API when published; this is the fallback / staff-provided passphrase.",
        secret = true,
        section = seasonalEventPassphraseSection,
        position = 2
    )
    default String seasonalEventPassphrase()
    {
        return "";
    }

    @ConfigItem(
        keyName = "showSeasonalEventDateTime",
        name = "Show Date & Time",
        description = "Show the current local date and time beside the Seasonal Event Passphrase",
        section = seasonalEventPassphraseSection,
        position = 3
    )
    default boolean showSeasonalEventDateTime()
    {
        return false;
    }

    @ConfigItem(
        keyName = "seasonalEventPassphraseColor",
        name = "Passphrase Color",
        description = "Color of the Seasonal Event Passphrase text",
        section = seasonalEventPassphraseSection,
        position = 4
    )
    default Color seasonalEventPassphraseColor()
    {
        return new Color(232, 140, 36);
    }

    @ConfigItem(
        keyName = "seasonalEventDateTimeColor",
        name = "Date & Time Color",
        description = "Color of the date and time text",
        section = seasonalEventPassphraseSection,
        position = 5
    )
    default Color seasonalEventDateTimeColor()
    {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "seasonalEventPassphraseBackgroundColor",
        name = "Background Color",
        description = "Background color behind the Seasonal Event Passphrase",
        section = seasonalEventPassphraseSection,
        position = 6
    )
    default Color seasonalEventPassphraseBackgroundColor()
    {
        return new Color(30, 30, 30, 180);
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

    // ========== Teams ==========

    @ConfigItem(
        keyName = "enableTeamIcons",
        name = "Enable team icons",
        description = "Show colored team badges for rostered players (edit rosters in the Teams side-panel tab)",
        section = teamsSection,
        position = 0
    )
    default boolean enableTeamIcons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableTeamRosterSync",
        name = "Sync team roster from API",
        description = "Fetch team names/colors/members from api.ironforged.gg (GET /plugins/seasonal/teams). Overwrites local Teams tab edits when sync runs.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        section = teamsSection,
        position = 1
    )
    default boolean enableTeamRosterSync()
    {
        return false;
    }

    @ConfigItem(
        keyName = "teamCount",
        name = "Number of teams",
        description = "How many teams to show in the Teams tab (1–10). Prefer editing from the Teams tab.",
        section = teamsSection,
        position = 2,
        hidden = true
    )
    @Range(min = 1, max = 10)
    default int teamCount()
    {
        return 2;
    }

    @ConfigItem(
        keyName = "nameplateTeamIcons",
        name = "Overhead icons",
        description = "Show team icons above players' heads next to the overhead name",
        section = teamsSection,
        position = 3
    )
    default boolean nameplateTeamIcons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "friendsListTeamIcons",
        name = "Friends list icons",
        description = "Show team icons next to names on the friends list",
        section = teamsSection,
        position = 4
    )
    default boolean friendsListTeamIcons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "clanListTeamIcons",
        name = "Clan member list icons",
        description = "Show team icons next to names in the clan and guest clan member lists",
        section = teamsSection,
        position = 5
    )
    default boolean clanListTeamIcons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "broadcastTeamIcons",
        name = "Broadcast icons",
        description = "Also show team icons on collection log and drop broadcasts in clan chat",
        section = teamsSection,
        position = 6
    )
    default boolean broadcastTeamIcons()
    {
        return true;
    }
}
