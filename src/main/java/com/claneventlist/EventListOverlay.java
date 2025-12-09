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
 * Overlay that displays clan events with dynamic sizing.
 * Supports both verbose and simple modes.
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
        setMovable(true);
        setResizable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        ClanEvent activeEvent = sheetService.getActiveEvent();
        ClanEvent nextEvent = sheetService.getNextEvent();
        
        // Nothing to show if no active or upcoming events
        if (activeEvent == null && nextEvent == null)
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setBackgroundColor(config.overlayBackgroundColor());
        
        // Use resized width if available, otherwise default
        Dimension size = getPreferredSize();
        int width = (size != null && size.width > 0) ? size.width : 180;
        panelComponent.setPreferredSize(new Dimension(width, 0));
        
        // Calculate how many chars fit per line
        int charsPerLine = Math.max(15, width / 7);

        if (config.simpleOverlay())
        {
            renderSimple(activeEvent, nextEvent, charsPerLine);
        }
        else
        {
            renderVerbose(activeEvent, nextEvent, charsPerLine);
        }

        return panelComponent.render(graphics);
    }

    /**
     * Simple overlay: just labels and times, no event names
     */
    private void renderSimple(ClanEvent activeEvent, ClanEvent nextEvent, int charsPerLine)
    {
        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Clan Events")
            .color(config.overlayAccentColor())
            .build());

        // Show current event if one is happening
        if (activeEvent != null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Event")
                .leftColor(config.activeColor())
                .right("NOW")
                .rightColor(config.activeColor())
                .build());
        }

        // Show next upcoming event
        if (nextEvent != null && (activeEvent == null || !nextEvent.equals(activeEvent)))
        {
            Color labelColor = nextEvent.isImminent() ? config.imminentColor() : config.overlayAccentColor();
            Color timeColor = nextEvent.isImminent() ? config.imminentColor() : config.overlayTextColor();
            
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Next Event")
                .leftColor(labelColor)
                .right(nextEvent.getTimeUntil())
                .rightColor(timeColor)
                .build());
        }
    }

    /**
     * Verbose overlay: titles, multi-line names, separate countdown
     */
    private void renderVerbose(ClanEvent activeEvent, ClanEvent nextEvent, int charsPerLine)
    {
        // Show active event if one is happening
        if (activeEvent != null)
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Active Event")
                .color(config.activeColor())
                .build());

            // Add event name - split into multiple lines if needed
            addWrappedName(activeEvent.getName(), config.overlayTextColor(), charsPerLine);

            panelComponent.getChildren().add(LineComponent.builder()
                .left("NOW!")
                .leftColor(config.activeColor())
                .build());
        }

        // Show next upcoming event (if different from active)
        if (nextEvent != null && (activeEvent == null || !nextEvent.equals(activeEvent)))
        {
            // Add spacing if we showed an active event
            if (activeEvent != null)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("")
                    .build());
            }

            Color accentColor = nextEvent.isImminent() ? config.imminentColor() : config.overlayAccentColor();
            Color timeColor = nextEvent.isImminent() ? config.imminentColor() : config.overlayTextColor();

            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Next Event")
                .color(accentColor)
                .build());

            // Add event name - split into multiple lines if needed
            addWrappedName(nextEvent.getName(), config.overlayTextColor(), charsPerLine);

            panelComponent.getChildren().add(LineComponent.builder()
                .left(nextEvent.getTimeUntil())
                .leftColor(timeColor)
                .build());
        }
    }

    /**
     * Adds the event name to the panel, wrapping to multiple lines if needed.
     */
    private void addWrappedName(String name, Color color, int charsPerLine)
    {
        if (name == null || name.isEmpty())
        {
            return;
        }

        // Split name into lines that fit the width
        int maxLines = 3; // Limit to 3 lines max
        int currentLine = 0;
        int start = 0;
        
        while (start < name.length() && currentLine < maxLines)
        {
            int end = Math.min(start + charsPerLine, name.length());
            
            // Try to break at a space if we're not at the end
            if (end < name.length() && name.charAt(end) != ' ')
            {
                int lastSpace = name.lastIndexOf(' ', end);
                if (lastSpace > start)
                {
                    end = lastSpace;
                }
            }
            
            String line = name.substring(start, end).trim();
            
            // Add ellipsis if we're truncating
            if (currentLine == maxLines - 1 && end < name.length())
            {
                line = line + "...";
            }
            
            panelComponent.getChildren().add(LineComponent.builder()
                .left(line)
                .leftColor(color)
                .build());
            
            start = end;
            // Skip the space we broke at
            if (start < name.length() && name.charAt(start) == ' ')
            {
                start++;
            }
            currentLine++;
        }
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
