package com.claneventlist;

import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collection-log style view of the seasonal allowlist: boss headers (hiscore sprites)
 * with a grid of item icons underneath.
 */
final class SeasonalAllowedDropsView
{
    private static final Color SLOT_BG = new Color(40, 37, 32);
    private static final Color SLOT_BORDER = new Color(70, 64, 55);
    private static final Color HEADER_BG = new Color(48, 44, 38);
    private static final int BOSS_ICON = 20;
    private static final int SLOT_SIZE = 32;
    private static final int DIALOG_COLS = 6;
    /** Side panel: 3 columns fit PluginPanel width with padding. */
    private static final int PANEL_COLS = 3;

    private SeasonalAllowedDropsView()
    {
    }

    /** Dialog / wide layout (6 columns) wrapped in a scroll pane. */
    static JScrollPane build(
        List<SeasonalCatalogEntry> catalog,
        SpriteManager spriteManager,
        ItemManager itemManager
    )
    {
        JPanel root = buildContent(catalog, spriteManager, itemManager, DIALOG_COLS);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(root);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(340, 420));
        return scroll;
    }

    /**
     * Side-panel embed: 3-column grid, no outer scroll (parent tab scrolls).
     */
    static JPanel buildPanel(
        List<SeasonalCatalogEntry> catalog,
        SpriteManager spriteManager,
        ItemManager itemManager
    )
    {
        JPanel root = buildContent(catalog, spriteManager, itemManager, PANEL_COLS);
        root.setBorder(new EmptyBorder(2, 0, 2, 0));
        root.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return root;
    }

    private static JPanel buildContent(
        List<SeasonalCatalogEntry> catalog,
        SpriteManager spriteManager,
        ItemManager itemManager,
        int gridCols
    )
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setOpaque(true);
        root.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (catalog == null || catalog.isEmpty())
        {
            JLabel empty = new JLabel("No allowed drops loaded");
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            root.add(empty);
        }
        else
        {
            Map<String, List<SeasonalCatalogEntry>> byBoss = groupByBoss(catalog);
            for (Map.Entry<String, List<SeasonalCatalogEntry>> group : byBoss.entrySet())
            {
                root.add(createBossSection(group.getKey(), group.getValue(), spriteManager, itemManager, gridCols));
                root.add(Box.createVerticalStrut(8));
            }
        }
        return root;
    }

    private static Map<String, List<SeasonalCatalogEntry>> groupByBoss(List<SeasonalCatalogEntry> catalog)
    {
        Map<String, List<SeasonalCatalogEntry>> map = new LinkedHashMap<>();
        for (SeasonalCatalogEntry entry : catalog)
        {
            map.computeIfAbsent(entry.getBoss(), k -> new ArrayList<>()).add(entry);
        }
        return map;
    }

    private static JPanel createBossSection(
        String bossName,
        List<SeasonalCatalogEntry> entries,
        SpriteManager spriteManager,
        ItemManager itemManager,
        int gridCols
    )
    {
        JPanel section = new JPanel(new BorderLayout(0, 4));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SLOT_BORDER),
            new EmptyBorder(4, 6, 4, 6)));

        JLabel bossIcon = new JLabel();
        bossIcon.setPreferredSize(new Dimension(BOSS_ICON, BOSS_ICON));
        HiscoreSkill skill = resolveBossSkill(bossName);
        if (skill != null && skill.getSpriteId() > 0)
        {
            spriteManager.getSpriteAsync(skill.getSpriteId(), 0, sprite ->
                SwingUtilities.invokeLater(() ->
                {
                    if (sprite == null)
                    {
                        return;
                    }
                    BufferedImage scaled = ImageUtil.resizeImage(
                        ImageUtil.resizeCanvas(sprite, 25, 25), BOSS_ICON, BOSS_ICON);
                    bossIcon.setIcon(new ImageIcon(scaled));
                }));
        }

        JLabel title = new JLabel(bossName);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setToolTipText(bossName + " · " + entries.size() + " allowed");

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setOpaque(false);
        titleRow.add(bossIcon);
        titleRow.add(title);
        header.add(titleRow, BorderLayout.WEST);

        JLabel count = new JLabel(String.valueOf(entries.size()));
        count.setFont(FontManager.getRunescapeSmallFont());
        count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.add(count, BorderLayout.EAST);

        section.add(header, BorderLayout.NORTH);

        int cols = Math.max(1, gridCols);
        int rows = Math.max(1, (entries.size() + cols - 1) / cols);
        JPanel grid = new JPanel(new GridLayout(rows, cols, 3, 3));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(2, 0, 2, 0));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (SeasonalCatalogEntry entry : entries)
        {
            grid.add(createItemSlot(entry, itemManager));
        }
        int pad = rows * cols - entries.size();
        for (int i = 0; i < pad; i++)
        {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            filler.setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
            grid.add(filler);
        }

        section.add(grid, BorderLayout.CENTER);
        return section;
    }

    private static JPanel createItemSlot(SeasonalCatalogEntry entry, ItemManager itemManager)
    {
        JPanel slot = new JPanel(new BorderLayout());
        slot.setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
        slot.setMinimumSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
        slot.setMaximumSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
        slot.setBackground(SLOT_BG);
        slot.setBorder(BorderFactory.createLineBorder(SLOT_BORDER));
        slot.setToolTipText(entry.getBoss() + " — " + entry.getItem()
            + (entry.getItemId() > 0 ? " (#" + entry.getItemId() + ")" : ""));

        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.CENTER);
        slot.add(iconLabel, BorderLayout.CENTER);

        int itemId = entry.getItemId();
        if (itemId <= 0)
        {
            itemId = resolveItemId(itemManager, entry.getItem());
        }
        if (itemId > 0)
        {
            AsyncBufferedImage image = itemManager.getImage(itemId);
            if (image != null)
            {
                image.addTo(iconLabel);
            }
        }
        else
        {
            iconLabel.setText("?");
            iconLabel.setFont(FontManager.getRunescapeSmallFont());
            iconLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }

        return slot;
    }

    private static int resolveItemId(ItemManager itemManager, String itemName)
    {
        if (itemName == null || itemName.isEmpty())
        {
            return -1;
        }
        try
        {
            List<ItemPrice> results = itemManager.search(itemName);
            if (results == null || results.isEmpty())
            {
                return -1;
            }
            String want = SeasonalCatalogEntry.normalizeKey(itemName);
            for (ItemPrice price : results)
            {
                if (SeasonalCatalogEntry.normalizeKey(price.getName()).equals(want))
                {
                    return price.getId();
                }
            }
            return results.get(0).getId();
        }
        catch (Exception e)
        {
            return -1;
        }
    }

    static HiscoreSkill resolveBossSkill(String bossName)
    {
        if (bossName == null || bossName.isEmpty())
        {
            return null;
        }
        String key = SeasonalCatalogEntry.normalizeKey(bossName);
        HiscoreSkill partial = null;
        for (HiscoreSkill skill : HiscoreSkill.values())
        {
            if (skill.getType() != HiscoreSkillType.BOSS)
            {
                continue;
            }
            String sk = SeasonalCatalogEntry.normalizeKey(skill.getName());
            if (sk.equals(key))
            {
                return skill;
            }
            if (partial == null && (sk.contains(key) || key.contains(sk)))
            {
                partial = skill;
            }
        }
        if (partial != null)
        {
            return partial;
        }
        return aliasBoss(key);
    }

    private static HiscoreSkill aliasBoss(String key)
    {
        if (key.contains("graardor") || key.equals("bandos"))
        {
            return HiscoreSkill.GENERAL_GRAARDOR;
        }
        if (key.contains("zilyana") || key.equals("saradomin"))
        {
            return HiscoreSkill.COMMANDER_ZILYANA;
        }
        if (key.contains("kree") || key.equals("armadyl"))
        {
            return HiscoreSkill.KREEARRA;
        }
        if (key.contains("tsutsaroth") || key.contains("kril") || key.equals("zamorak"))
        {
            return HiscoreSkill.KRIL_TSUTSAROTH;
        }
        if (key.contains("jad") && !key.contains("zuk"))
        {
            return HiscoreSkill.TZTOK_JAD;
        }
        if (key.contains("zuk"))
        {
            return HiscoreSkill.TZKAL_ZUK;
        }
        if (key.contains("cox") || key.contains("chambers"))
        {
            return key.contains("challenge")
                ? HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE
                : HiscoreSkill.CHAMBERS_OF_XERIC;
        }
        if (key.contains("tob") || key.contains("theatre"))
        {
            return key.contains("hard")
                ? HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE
                : HiscoreSkill.THEATRE_OF_BLOOD;
        }
        if (key.contains("toa") || key.contains("amascut") || key.contains("tombs"))
        {
            return key.contains("expert")
                ? HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT
                : HiscoreSkill.TOMBS_OF_AMASCUT;
        }
        if (key.contains("gauntlet"))
        {
            return key.contains("corrupt")
                ? HiscoreSkill.THE_CORRUPTED_GAUNTLET
                : HiscoreSkill.THE_GAUNTLET;
        }
        if (key.contains("barrows"))
        {
            return HiscoreSkill.BARROWS_CHESTS;
        }
        if (key.contains("royal titan"))
        {
            return HiscoreSkill.THE_ROYAL_TITANS;
        }
        return null;
    }
}
