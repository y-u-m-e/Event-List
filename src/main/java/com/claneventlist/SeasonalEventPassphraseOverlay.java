package com.claneventlist;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Movable overlay that draws the Seasonal Event Passphrase
 * (API value when published, else config).
 */
public class SeasonalEventPassphraseOverlay extends OverlayPanel
{
    private static final DateTimeFormatter DATE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("M/d/yyyy h:mm a");
    private static final int HORIZONTAL_PAD = 16;
    private static final int VERTICAL_PAD = 8;
    private static final int BETWEEN_PAD = 12;

    private final EventListConfig config;
    private final SeasonalCatalogService catalogService;

    public SeasonalEventPassphraseOverlay(EventListConfig config, SeasonalCatalogService catalogService)
    {
        this.config = config;
        this.catalogService = catalogService;
        setPosition(OverlayPosition.TOP_CENTER);
        setMovable(true);
        setSnappable(true);
        setResizable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showSeasonalEventPassphrase())
        {
            return null;
        }

        String passphrase = catalogService.resolveEventPassphrase(config);
        if (passphrase == null || passphrase.isEmpty())
        {
            return null;
        }

        boolean showDateTime = config.showSeasonalEventDateTime();
        String dateTime = showDateTime ? LocalDateTime.now().format(DATE_TIME_FORMAT) : "";

        panelComponent.getChildren().clear();
        panelComponent.setBackgroundColor(config.seasonalEventPassphraseBackgroundColor());

        LineComponent.LineComponentBuilder line = LineComponent.builder()
            .left(passphrase)
            .leftColor(config.seasonalEventPassphraseColor());
        if (showDateTime)
        {
            line.right(dateTime).rightColor(config.seasonalEventDateTimeColor());
        }
        panelComponent.getChildren().add(line.build());

        FontMetrics metrics = graphics.getFontMetrics();
        int contentWidth = metrics.stringWidth(passphrase) + HORIZONTAL_PAD;
        if (showDateTime)
        {
            contentWidth += metrics.stringWidth(dateTime) + BETWEEN_PAD;
        }
        int contentHeight = metrics.getHeight() + VERTICAL_PAD;

        setMinimumSize(contentHeight);

        Dimension preferred = getPreferredSize();
        if (preferred == null)
        {
            panelComponent.setPreferredSize(new Dimension(contentWidth, 0));
        }
        else
        {
            int width = Math.max(preferred.width, contentWidth);
            int height = Math.max(preferred.height, contentHeight);
            if (width != preferred.width || height != preferred.height)
            {
                setPreferredSize(new Dimension(width, height));
            }
        }

        return super.render(graphics);
    }
}
