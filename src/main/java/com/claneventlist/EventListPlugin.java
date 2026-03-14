package com.claneventlist;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@PluginDescriptor(
    name = "Clan Event List",
    description = "Display clan events from a Google Sheet with an overlay and side panel",
    tags = {"clan", "events", "calendar", "schedule", "google", "sheet"}
)
public class EventListPlugin extends Plugin
{
    private static final String ICON_PATH = "calendar_icon.png";

    @Inject
    private EventListConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private GoogleSheetService sheetService;

    @Inject
    private ScheduledExecutorService executorService;

    @Inject
    private SeasonalReporterService seasonalReporterService;

    private EventListOverlay overlay;
    private EventListPanel panel;
    private NavigationButton navButton;
    private boolean initialized = false;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Clan Event List plugin started");

        // Create overlay
        overlay = new EventListOverlay(config, sheetService);
        overlayManager.add(overlay);

        // Create panel
        panel = new EventListPanel(
            config,
            sheetService,
            this::refreshEvents,
            this::linkSeasonal,
            this::testSeasonalApi,
            this::flushSeasonalQueue,
            this::refreshSeasonalManifest,
            this::enqueueSeasonalDebugDrop
        );

        // Create navigation button with icon
        BufferedImage icon = createCalendarIcon();

        navButton = NavigationButton.builder()
            .tooltip("Clan Events")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        // Initial fetch
        initialized = true;
        seasonalReporterService.start();
        refreshEvents();
        updateSeasonalTelemetry();
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Clan Event List plugin stopped");

        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);

        initialized = false;
        overlay = null;
        panel = null;
        navButton = null;
        seasonalReporterService.stop();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN && initialized)
        {
            // Refresh events when logging in if cache is stale
            if (sheetService.needsRefresh(config.refreshInterval()))
            {
                refreshEvents();
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals(EventListConfig.CONFIG_GROUP))
        {
            return;
        }

        // Refresh when sheet configuration changes
        if (event.getKey().equals("sheetId") || event.getKey().equals("sheetName"))
        {
            refreshEvents();
        }

        // Update panel display settings
        if (event.getKey().equals("showPastEvents") || event.getKey().equals("maxEventsShown"))
        {
            if (panel != null)
            {
                panel.updateEvents();
            }
        }

        if (event.getKey().equals("seasonalEnabled") || event.getKey().equals("seasonalDryRun"))
        {
            updateSeasonalTelemetry();
        }

        if (event.getKey().equals("seasonalLinkNow") && "true".equals(event.getNewValue()))
        {
            configManager.setConfiguration(EventListConfig.CONFIG_GROUP, "seasonalLinkNow", false);
            linkSeasonal();
        }

        if (event.getKey().equals("seasonalTestApiNow") && "true".equals(event.getNewValue()))
        {
            configManager.setConfiguration(EventListConfig.CONFIG_GROUP, "seasonalTestApiNow", false);
            testSeasonalApi();
        }

        if (event.getKey().equals("seasonalFlushQueueNow") && "true".equals(event.getNewValue()))
        {
            configManager.setConfiguration(EventListConfig.CONFIG_GROUP, "seasonalFlushQueueNow", false);
            flushSeasonalQueue();
        }
    }

    /**
     * Scheduled task to periodically refresh events.
     * Runs every minute and checks if a refresh is needed based on config.
     */
    @Schedule(period = 1, unit = ChronoUnit.MINUTES)
    public void scheduledRefresh()
    {
        if (!initialized)
        {
            return;
        }

        if (sheetService.needsRefresh(config.refreshInterval()))
        {
            log.debug("Scheduled refresh triggered");
            refreshEvents();
        }
        else
        {
            // Just update the panel to refresh time displays
            if (panel != null)
            {
                panel.updateEvents();
            }
        }
    }

    /**
     * Scheduled task to process seasonal queue in the background.
     */
    @Schedule(period = 10, unit = ChronoUnit.SECONDS)
    public void scheduledSeasonalQueue()
    {
        if (!initialized)
        {
            return;
        }

        executorService.submit(() ->
        {
            seasonalReporterService.processQueue();
            updateSeasonalTelemetry();
        });
    }

    @Schedule(period = 5, unit = ChronoUnit.MINUTES)
    public void scheduledSeasonalManifestRefresh()
    {
        if (!initialized)
        {
            return;
        }
        executorService.submit(() ->
        {
            seasonalReporterService.refreshManifest();
            updateSeasonalTelemetry();
        });
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        seasonalReporterService.onGameTick();
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        seasonalReporterService.onNpcLootReceived(event);
        updateSeasonalTelemetry();
    }

    /**
     * Refreshes the event data from Google Sheets.
     */
    public void refreshEvents()
    {
        if (!initialized)
        {
            return;
        }

        String sheetId = config.sheetId();
        if (sheetId == null || sheetId.trim().isEmpty())
        {
            log.debug("No sheet ID configured, clearing events");
            // Clear events when sheet ID is removed
            sheetService.clearEvents();
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

        executorService.submit(() -> {
            try
            {
                sheetService.fetchEvents(config.sheetId(), config.sheetName());

                if (panel != null)
                {
                    panel.updateEvents();
                    updateSeasonalTelemetry();
                }

                log.debug("Events refreshed successfully");
            }
            catch (Exception e)
            {
                log.warn("Failed to refresh events: {}", e.getMessage());
                if (panel != null)
                {
                    panel.showError("Failed to fetch events.<br>Check your Sheet ID.");
                }
            }
        });
    }

    private void updateSeasonalTelemetry()
    {
        if (panel != null)
        {
            panel.updateSeasonalTelemetry(seasonalReporterService.getTelemetry());
        }
    }

    private void linkSeasonal()
    {
        executorService.submit(() ->
        {
            seasonalReporterService.linkWithConnectCode(config.seasonalConnectCode());
            updateSeasonalTelemetry();
        });
    }

    private void testSeasonalApi()
    {
        executorService.submit(() ->
        {
            seasonalReporterService.testApi();
            updateSeasonalTelemetry();
        });
    }

    private void flushSeasonalQueue()
    {
        executorService.submit(() ->
        {
            seasonalReporterService.flushQueueNow();
            updateSeasonalTelemetry();
        });
    }

    private void refreshSeasonalManifest()
    {
        executorService.submit(() ->
        {
            seasonalReporterService.refreshManifest();
            updateSeasonalTelemetry();
        });
    }

    private void enqueueSeasonalDebugDrop()
    {
        executorService.submit(() ->
        {
            seasonalReporterService.enqueueDebugDrop();
            updateSeasonalTelemetry();
        });
    }

    /**
     * Creates a simple calendar icon for the navigation button.
     */
    private BufferedImage createCalendarIcon()
    {
        // Try to load from resources first
        try
        {
            return ImageUtil.loadImageResource(getClass(), ICON_PATH);
        }
        catch (Exception e)
        {
            log.debug("Calendar icon not found, creating programmatically");
        }

        // Create a simple calendar icon programmatically
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = icon.createGraphics();
        
        // Enable anti-aliasing
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, 
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Calendar body (white/light gray)
        g.setColor(new java.awt.Color(220, 220, 220));
        g.fillRoundRect(1, 3, 14, 12, 2, 2);

        // Calendar header (gold/accent)
        g.setColor(new java.awt.Color(255, 200, 0));
        g.fillRoundRect(1, 3, 14, 4, 2, 2);
        g.fillRect(1, 5, 14, 2);

        // Calendar rings
        g.setColor(new java.awt.Color(100, 100, 100));
        g.fillRect(4, 1, 2, 4);
        g.fillRect(10, 1, 2, 4);

        // Calendar dots (representing dates)
        g.setColor(new java.awt.Color(80, 80, 80));
        g.fillRect(3, 9, 2, 2);
        g.fillRect(7, 9, 2, 2);
        g.fillRect(11, 9, 2, 2);
        g.fillRect(3, 12, 2, 2);
        g.fillRect(7, 12, 2, 2);

        g.dispose();
        return icon;
    }

    @Provides
    EventListConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(EventListConfig.class);
    }
}

