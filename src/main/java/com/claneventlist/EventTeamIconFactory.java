package com.claneventlist;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Official OSRS clan-color icons ({@code Clan icon - Red.png}, etc.) for team badges.
 * Source: oldschool.runescape.wiki (Jagex interface sprites).
 */
@Slf4j
final class EventTeamIconFactory
{
    /**
     * Chat / overhead: must match {@code ChatIconManager.IMAGE_DIMENSION} (11×11).
     * Official wiki clan icons are 13×13 and get fitted onto this canvas.
     */
    static final int BADGE_SIZE = 11;

    /** Friends / clan side lists: Friend Notes layout (14×12). */
    static final int FRIENDS_ICON_WIDTH = 14;
    static final int FRIENDS_ICON_HEIGHT = 12;

    static final int PALETTE_SIZE = 8;

    /**
     * The eight official clan-icon colors for team badges.
     */
    enum TeamPalette
    {
        RED("red", "Red", 0xC62828),
        ORANGE("orange", "Orange", 0xE65100),
        YELLOW("yellow", "Yellow", 0xC9A227),
        GREEN("green", "Green", 0x2E7D32),
        BLUE("blue", "Blue", 0x1565C0),
        PURPLE("purple", "Purple", 0x6A1B9A),
        PINK("pink", "Pink", 0xAD1457),
        GREY("grey", "Grey", 0x616161);

        private final String key;
        private final String label;
        private final int rgb;

        TeamPalette(String key, String label, int rgb)
        {
            this.key = key;
            this.label = label;
            this.rgb = rgb;
        }

        String getKey()
        {
            return key;
        }

        String getLabel()
        {
            return label;
        }

        Color getColor()
        {
            return new Color(rgb);
        }

        static TeamPalette byIndex(int index)
        {
            TeamPalette[] values = values();
            if (index < 0)
            {
                return RED;
            }
            return values[index % values.length];
        }

        static TeamPalette fromKey(String key)
        {
            if (key == null || key.isEmpty())
            {
                return null;
            }
            String normalized = key.trim().toLowerCase();
            // Legacy keys from the warhammer palette.
            if ("gray".equals(normalized))
            {
                normalized = "grey";
            }
            if ("magenta".equals(normalized))
            {
                normalized = "pink";
            }
            if ("gold".equals(normalized))
            {
                normalized = "yellow";
            }
            if ("teal".equals(normalized) || "cyan".equals(normalized))
            {
                normalized = "blue";
            }
            if ("brown".equals(normalized))
            {
                normalized = "orange";
            }
            for (TeamPalette p : values())
            {
                if (p.key.equals(normalized) || p.label.equalsIgnoreCase(key.trim()))
                {
                    return p;
                }
            }
            return null;
        }

        static TeamPalette nearest(Color color)
        {
            if (color == null)
            {
                return RED;
            }
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();
            TeamPalette best = RED;
            long bestDist = Long.MAX_VALUE;
            for (TeamPalette p : values())
            {
                Color c = p.getColor();
                long dr = r - c.getRed();
                long dg = g - c.getGreen();
                long db = b - c.getBlue();
                long dist = dr * dr + dg * dg + db * db;
                if (dist < bestDist)
                {
                    bestDist = dist;
                    best = p;
                }
            }
            return best;
        }
    }

    private EventTeamIconFactory()
    {
    }

    static TeamPalette defaultPalette(int teamNumber)
    {
        return TeamPalette.byIndex(Math.max(0, teamNumber - 1));
    }

    /** 11×11 badge for chat names and overhead. */
    static BufferedImage loadBadge(TeamPalette palette)
    {
        return loadSized(palette, BADGE_SIZE, BADGE_SIZE);
    }

    /** 14×12 badge for friends/clan lists (same dimensions as Friend Notes). */
    static BufferedImage loadFriendsListBadge(TeamPalette palette)
    {
        return loadSized(palette, FRIENDS_ICON_WIDTH, FRIENDS_ICON_HEIGHT);
    }

    private static BufferedImage loadSized(TeamPalette palette, int width, int height)
    {
        TeamPalette p = palette == null ? TeamPalette.RED : palette;
        try
        {
            BufferedImage raw = ImageUtil.loadImageResource(
                EventTeamIconFactory.class, "icons/clan-icon-" + p.getKey() + ".png");
            BufferedImage argb = toArgb(raw);
            BufferedImage fitted = ImageUtil.resizeImage(argb, width, height, true);
            return ImageUtil.resizeCanvas(fitted, width, height);
        }
        catch (Exception e)
        {
            log.debug("Clan icon {} failed to load, drawing fallback: {}", p.getKey(), e.getMessage());
            BufferedImage fallback = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fallback.createGraphics();
            g.setColor(p.getColor());
            g.fillRect(1, 1, Math.max(1, width - 2), Math.max(1, height - 2));
            g.dispose();
            return fallback;
        }
    }

    private static BufferedImage toArgb(BufferedImage src)
    {
        if (src == null)
        {
            return null;
        }
        if (src.getType() == BufferedImage.TYPE_INT_ARGB)
        {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
