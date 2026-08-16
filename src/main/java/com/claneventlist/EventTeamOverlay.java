package com.claneventlist;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Draws team badges above rostered players' heads (overhead name position).
 */
class EventTeamOverlay extends Overlay
{
    private static final int ACTOR_OVERHEAD_TEXT_MARGIN = 40;

    private final Client client;
    private final EventTeamService teamService;
    private final EventListConfig config;

    @Inject
    EventTeamOverlay(Client client, EventTeamService teamService, EventListConfig config)
    {
        this.client = client;
        this.teamService = teamService;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_MED);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.enableTeamIcons() || !config.nameplateTeamIcons() || !teamService.hasRoster())
        {
            return null;
        }

        WorldView wv = client.getTopLevelWorldView();
        if (wv == null)
        {
            return null;
        }

        FontMetrics fm = graphics.getFontMetrics();
        Player localPlayer = client.getLocalPlayer();
        for (Player player : wv.players())
        {
            if (player == localPlayer)
            {
                continue;
            }

            String name = player.getName();
            if (name == null || !wv.contains(player.getLocalLocation()))
            {
                continue;
            }

            Integer team = teamService.teamFor(Text.standardize(name));
            if (team == null)
            {
                continue;
            }

            BufferedImage badge = teamService.badgeImage(team);
            if (badge == null)
            {
                continue;
            }

            Point textLocation = player.getCanvasTextLocation(
                graphics, name, player.getLogicalHeight() + ACTOR_OVERHEAD_TEXT_MARGIN);
            if (textLocation == null)
            {
                continue;
            }

            Point imageLocation = new Point(
                textLocation.getX() - badge.getWidth() - 5,
                textLocation.getY() - fm.getHeight() / 2 - badge.getHeight() / 2 + 2);
            OverlayUtil.renderImageLocation(graphics, imageLocation, badge);
        }

        return null;
    }
}
