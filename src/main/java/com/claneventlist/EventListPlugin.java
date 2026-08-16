package com.claneventlist;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@PluginDescriptor(
    name = "Ironforged Clan Events",
    description = "Display Iron Forged clan events from api.ironforged.gg in a side panel, with optional Seasonal Event Passphrase overlay and notable-drop submit",
    tags = {"clan", "events", "calendar", "schedule", "seasonal", "ironforged", "teams"}
)
public class EventListPlugin extends Plugin
{
    private static final String ICON_PATH = "ironforged_icon.png";
    private static final int NAV_ICON_SIZE = 16;

    @Inject
    private EventListConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ColorPickerManager colorPickerManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private EventsApiService eventsApiService;

    @Inject
    private SeasonalCatalogService seasonalCatalogService;

    @Inject
    private SeasonalDropSubmitService seasonalDropSubmitService;

    @Inject
    private EventTeamService eventTeamService;

    @Inject
    private EventTeamSyncService eventTeamSyncService;

    @Inject
    private EventTeamOverlay eventTeamOverlay;

    @Inject
    private ScheduledExecutorService executorService;

    private SeasonalEventPassphraseOverlay passphraseOverlay;
    private EventListPanel panel;
    private NavigationButton navButton;
    private boolean initialized = false;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Ironforged Clan Events plugin started");

        migrateLegacyBingoConfig();
        // Free-text host was replaced by the Use dev API toggle.
        configManager.unsetConfiguration(EventListConfig.CONFIG_GROUP, "pluginApiBase");

        passphraseOverlay = new SeasonalEventPassphraseOverlay(config, seasonalCatalogService);
        overlayManager.add(passphraseOverlay);
        overlayManager.add(eventTeamOverlay);

        panel = new EventListPanel(
            config,
            configManager,
            colorPickerManager,
            itemManager,
            spriteManager,
            eventsApiService,
            seasonalCatalogService,
            seasonalDropSubmitService,
            eventTeamService,
            eventTeamSyncService,
            this::refreshEvents,
            this::refreshSeasonalCatalog,
            this::testSeasonalSubmit,
            this::syncTeams
        );

        BufferedImage icon = loadPluginIcon();

        navButton = NavigationButton.builder()
            .tooltip("Ironforged Clan Events")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        initialized = true;
        seasonalDropSubmitService.setStatusListener(() ->
        {
            if (panel != null)
            {
                panel.updateSeasonalSection();
            }
        });
        seasonalDropSubmitService.start();
        eventTeamService.start();
        refreshEvents();
        refreshSeasonalCatalog();
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Ironforged Clan Events plugin stopped");

        overlayManager.remove(passphraseOverlay);
        overlayManager.remove(eventTeamOverlay);
        clientToolbar.removeNavigation(navButton);
        seasonalCatalogService.clear();
        seasonalDropSubmitService.setStatusListener(null);
        eventTeamService.stop();

        initialized = false;
        passphraseOverlay = null;
        panel = null;
        navButton = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN && initialized)
        {
            seasonalDropSubmitService.refreshProfileKey();
            eventTeamService.refreshSurfaces();
            if (panel != null)
            {
                panel.updateSeasonalSection();
                panel.refreshTeamsOnlineCounts();
            }
            if (eventsApiService.needsRefresh(config.refreshInterval()))
            {
                refreshEvents();
            }
            if ((config.enableSeasonalSubmit() || config.showSeasonalEventPassphrase())
                && seasonalCatalogService.needsRefresh(config.seasonalCatalogRefreshInterval()))
            {
                refreshSeasonalCatalog();
            }
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        seasonalDropSubmitService.onNpcLootReceived(event);
    }

    @Subscribe
    public void onPlayerLootReceived(PlayerLootReceived event)
    {
        seasonalDropSubmitService.onPlayerLootReceived(event);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        eventTeamService.onChatMessage(event);
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        eventTeamService.onScriptCallbackEvent(event);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        eventTeamService.onScriptPostFired(event);
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event)
    {
        if (panel != null)
        {
            panel.refreshTeamsOnlineCounts();
        }
    }

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event)
    {
        if (panel != null)
        {
            panel.refreshTeamsOnlineCounts();
        }
    }

    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event)
    {
        if (panel != null)
        {
            panel.refreshTeamsOnlineCounts();
        }
    }

    @Subscribe
    public void onFriendsChatChanged(FriendsChatChanged event)
    {
        if (panel != null)
        {
            panel.refreshTeamsOnlineCounts();
        }
    }

    @Subscribe
    public void onFriendsChatMemberJoined(FriendsChatMemberJoined event)
    {
        if (panel != null)
        {
            panel.refreshTeamsOnlineCounts();
        }
    }

    @Subscribe
    public void onFriendsChatMemberLeft(FriendsChatMemberLeft event)
    {
        if (panel != null)
        {
            panel.refreshTeamsOnlineCounts();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals(EventListConfig.CONFIG_GROUP))
        {
            return;
        }

        String key = event.getKey();
        if (key != null && (key.startsWith("team")
            || key.equals("enableTeamIcons")
            || key.equals("enableTeamRosterSync")
            || key.equals("teamCount")
            || key.equals("nameplateTeamIcons")
            || key.equals("friendsListTeamIcons")
            || key.equals("clanListTeamIcons")
            || key.equals("broadcastTeamIcons")))
        {
            eventTeamService.onConfigChanged(key);
            if (panel != null)
            {
                if ("teamCount".equals(key) || (key != null && key.startsWith("team") && key.endsWith("Names"))
                    || (key != null && key.startsWith("team") && key.endsWith("Label")))
                {
                    panel.rebuildTeamsUi();
                }
                else
                {
                    panel.refreshTeamsOnlineCounts();
                    panel.updateTeamsSyncLabel();
                }
            }
            if ("enableTeamRosterSync".equals(key) && config.enableTeamRosterSync())
            {
                syncTeams();
            }
        }

        if (event.getKey().equals("enableEventsFeed") || event.getKey().equals("useDevApi"))
        {
            refreshEvents();
            if (config.enableSeasonalSubmit() || config.showSeasonalEventPassphrase())
            {
                refreshSeasonalCatalog();
            }
            else if (panel != null)
            {
                panel.updateSeasonalSection();
            }
        }

        if (event.getKey().equals("enableSeasonalSubmit")
            || event.getKey().equals("showSeasonalEventPassphrase"))
        {
            if (config.enableSeasonalSubmit() || config.showSeasonalEventPassphrase())
            {
                refreshSeasonalCatalog();
            }
            else if (!config.enableSeasonalSubmit())
            {
                if (!config.showSeasonalEventPassphrase())
                {
                    seasonalCatalogService.clear();
                }
                if (panel != null)
                {
                    panel.updateSeasonalSection();
                }
            }
        }

        if (event.getKey().equals("showPastEvents") || event.getKey().equals("maxEventsShown"))
        {
            if (panel != null)
            {
                panel.updateEvents();
            }
        }
    }

    @Schedule(period = 1, unit = ChronoUnit.MINUTES)
    public void scheduledRefresh()
    {
        if (!initialized)
        {
            return;
        }

        if (eventsApiService.needsRefresh(config.refreshInterval()))
        {
            log.debug("Scheduled refresh triggered");
            refreshEvents();
        }
        else if (panel != null)
        {
            panel.updateEvents();
        }

        if ((config.enableSeasonalSubmit() || config.showSeasonalEventPassphrase())
            && seasonalCatalogService.needsRefresh(config.seasonalCatalogRefreshInterval()))
        {
            refreshSeasonalCatalog();
        }

        if (config.enableSeasonalSubmit())
        {
            seasonalDropSubmitService.processQueue();
        }

        if (config.enableTeamRosterSync()
            && eventTeamSyncService.needsRefresh(config.seasonalCatalogRefreshInterval()))
        {
            syncTeams();
        }

        if (panel != null && config.enableTeamIcons())
        {
            panel.refreshTeamsOnlineCounts();
        }
    }

    public void refreshEvents()
    {
        if (!initialized)
        {
            return;
        }

        if (!config.enableEventsFeed())
        {
            log.debug("Events feed disabled");
            eventsApiService.clearEvents();
            if (panel != null)
            {
                panel.updateEvents();
            }
            return;
        }

        if (panel != null)
        {
            panel.showLoading();
        }

        executorService.submit(() ->
        {
            try
            {
                eventsApiService.fetchEvents();

                if (panel != null)
                {
                    panel.updateEvents();
                }

                log.debug("Events refreshed successfully");
            }
            catch (Exception e)
            {
                log.warn("Failed to refresh events: {}", e.getMessage());
                if (panel != null)
                {
                    panel.showError("Failed to fetch events.<br>Try again later.");
                }
            }
        });
    }

    public void refreshSeasonalCatalog()
    {
        if (!initialized)
        {
            return;
        }
        if (!config.enableSeasonalSubmit() && !config.showSeasonalEventPassphrase())
        {
            return;
        }

        executorService.submit(() ->
        {
            try
            {
                seasonalCatalogService.refresh();
                log.debug(
                    "Seasonal catalog: {} entries id={} key={}",
                    seasonalCatalogService.catalogSize(),
                    seasonalCatalogService.getEventId(),
                    seasonalCatalogService.getEventPassphrase()
                );
            }
            catch (Exception e)
            {
                log.warn("Failed to refresh seasonal catalog: {}", e.getMessage());
            }
            finally
            {
                if (panel != null)
                {
                    panel.updateSeasonalSection();
                }
            }
        });
    }

    public void syncTeams()
    {
        if (!initialized)
        {
            return;
        }

        executorService.submit(() ->
        {
            boolean updated = eventTeamSyncService.syncNow();
            if (panel != null)
            {
                if (updated)
                {
                    panel.rebuildTeamsUi();
                }
                panel.updateTeamsSyncLabel();
            }
        });
    }

    public void testSeasonalSubmit()
    {
        if (!initialized)
        {
            return;
        }

        executorService.submit(() ->
        {
            if (config.enableSeasonalSubmit()
                && (seasonalCatalogService.catalogSize() == 0 || seasonalCatalogService.needsRefresh(1)))
            {
                seasonalCatalogService.refresh();
            }
            seasonalDropSubmitService.submitTestDrop();
            if (panel != null)
            {
                panel.updateSeasonalSection();
            }
        });
    }

    private BufferedImage loadPluginIcon()
    {
        try
        {
            BufferedImage image = ImageUtil.loadImageResource(getClass(), ICON_PATH);
            return ImageUtil.resizeImage(image, NAV_ICON_SIZE, NAV_ICON_SIZE);
        }
        catch (IllegalArgumentException e)
        {
            log.debug("Iron Forged icon resource missing, using drawn fallback");
        }

        BufferedImage icon = new BufferedImage(NAV_ICON_SIZE, NAV_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        int gold = 0xFFFFC800;
        int goldDark = 0xFFB48200;
        int body = 0xFFE6E6E6;
        int ink = 0xFF373737;
        int ring = 0xFF787878;
        int live = 0xFF64DC64;

        for (int x : new int[]{3, 4, 10, 11})
        {
            icon.setRGB(x, 1, ring);
            icon.setRGB(x, 2, ring);
        }

        for (int x = 1; x <= 14; x++)
        {
            icon.setRGB(x, 3, gold);
            icon.setRGB(x, 4, gold);
            icon.setRGB(x, 5, goldDark);
            for (int y = 6; y <= 14; y++)
            {
                icon.setRGB(x, y, body);
            }
        }

        for (int x = 1; x <= 14; x++)
        {
            icon.setRGB(x, 3, ink);
            icon.setRGB(x, 14, ink);
        }
        for (int y = 3; y <= 14; y++)
        {
            icon.setRGB(1, y, ink);
            icon.setRGB(14, y, ink);
        }

        int[][] dots = {
            {3, 7}, {6, 7}, {9, 7}, {12, 7},
            {3, 10}, {6, 10}, {9, 10},
            {3, 13}, {6, 13}
        };
        for (int[] p : dots)
        {
            icon.setRGB(p[0], p[1], ink);
            icon.setRGB(p[0] + 1, p[1], ink);
        }

        for (int x = 9; x <= 12; x++)
        {
            icon.setRGB(x, 10, gold);
            icon.setRGB(x, 11, gold);
        }
        icon.setRGB(10, 11, live);
        icon.setRGB(11, 11, live);

        return icon;
    }

    /**
     * Migrates bingo-* and intermediate keyword-* config keys to Seasonal Event Passphrase keys.
     */
    private void migrateLegacyBingoConfig()
    {
        migrateConfigKey("showBingoKeyword", "showSeasonalEventPassphrase");
        migrateConfigKey("showSeasonalEventKeyword", "showSeasonalEventPassphrase");

        migrateConfigKey("bingoKeyword", "seasonalEventPassphrase");
        migrateConfigKey("seasonalEventKeyword", "seasonalEventPassphrase");
        migrateConfigKey("seasonalSubmitPassphrase", "seasonalEventPassphrase");

        migrateConfigKey("showBingoDateTime", "showSeasonalEventDateTime");

        migrateConfigKey("bingoKeywordColor", "seasonalEventPassphraseColor");
        migrateConfigKey("seasonalEventKeywordColor", "seasonalEventPassphraseColor");

        migrateConfigKey("bingoDateTimeColor", "seasonalEventDateTimeColor");

        migrateConfigKey("bingoKeywordBackgroundColor", "seasonalEventPassphraseBackgroundColor");
        migrateConfigKey("seasonalEventKeywordBackgroundColor", "seasonalEventPassphraseBackgroundColor");
    }

    private void migrateConfigKey(String oldKey, String newKey)
    {
        String existing = configManager.getConfiguration(EventListConfig.CONFIG_GROUP, newKey);
        if (existing != null)
        {
            return;
        }
        String legacy = configManager.getConfiguration(EventListConfig.CONFIG_GROUP, oldKey);
        if (legacy == null)
        {
            return;
        }
        configManager.setConfiguration(EventListConfig.CONFIG_GROUP, newKey, legacy);
        configManager.unsetConfiguration(EventListConfig.CONFIG_GROUP, oldKey);
        log.debug("Migrated config {} -> {}", oldKey, newKey);
    }

    @Provides
    EventListConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(EventListConfig.class);
    }
}
