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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SeasonalPassphraseOverlay extends Overlay
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EventListConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public SeasonalPassphraseOverlay(EventListConfig config)
    {
        this.config = config;
        setPosition(OverlayPosition.TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.seasonalEnabled())
        {
            return null;
        }
        String passphrase = config.seasonalConnectCode() != null ? config.seasonalConnectCode().trim() : "";
        String eventId = config.seasonalEventId() != null ? config.seasonalEventId().trim() : "";
        if (passphrase.isEmpty() || eventId.isEmpty())
        {
            return null;
        }

        panel.getChildren().clear();
        panel.setBackgroundColor(new Color(10, 10, 10, 185));

        panel.getChildren().add(TitleComponent.builder()
            .text("Seasonal Active")
            .color(new Color(255, 200, 0))
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Event")
            .right(eventId)
            .leftColor(Color.LIGHT_GRAY)
            .rightColor(Color.WHITE)
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Passphrase")
            .right(passphrase)
            .leftColor(Color.LIGHT_GRAY)
            .rightColor(new Color(120, 230, 255))
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Date")
            .right(LocalDate.now().format(DATE_FMT))
            .leftColor(Color.LIGHT_GRAY)
            .rightColor(Color.WHITE)
            .build());
        return panel.render(graphics);
    }
}
