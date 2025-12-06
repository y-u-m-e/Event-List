package com.claneventlist;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Minimal overlay that displays the next upcoming clan event.
 * Shows only: Event name and countdown timer.
 */
public class EventListOverlay extends Overlay
{
    private final EventListConfig config;
    private final GoogleSheetService sheetService;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public EventListOverlay(EventListConfig config, GoogleSheetService sheetService)
    {
        this.config = config;
        this.sheetService = sheetService;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        ClanEvent nextEvent = sheetService.getNextEvent();
        if (nextEvent == null)
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setBackgroundColor(config.overlayBackgroundColor());
        panelComponent.setPreferredSize(new Dimension(160, 0));

        // Determine colors based on urgency
        Color accentColor = nextEvent.isImminent() ? config.imminentColor() : config.overlayAccentColor();
        Color timeColor = nextEvent.isImminent() ? config.imminentColor() : config.overlayTextColor();

        // Title: "Next Clan Event"
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Next Clan Event")
            .color(accentColor)
            .build());

        // Event name with countdown
        String timeUntil = nextEvent.getTimeUntil();
        String displayText = nextEvent.isHappeningNow() 
            ? "NOW!" 
            : timeUntil;
        
        panelComponent.getChildren().add(LineComponent.builder()
            .left(truncate(nextEvent.getName(), 15))
            .leftColor(config.overlayTextColor())
            .right(displayText)
            .rightColor(timeColor)
            .build());

        return panelComponent.render(graphics);
    }

    /**
     * Truncates a string to the specified length with ellipsis.
     */
    private String truncate(String text, int maxLength)
    {
        if (text == null)
        {
            return "";
        }
        if (text.length() <= maxLength)
        {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}

