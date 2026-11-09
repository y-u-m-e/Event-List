package com.claneventlist;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Side panel: vertical timeline rail for Ironforged clan events.
 */
@Slf4j
public class EventListPanel extends PluginPanel
{
    private static final Color BACKGROUND_COLOR = ColorScheme.DARK_GRAY_COLOR;
    private static final Color PANEL_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color HOVER_COLOR = ColorScheme.DARK_GRAY_HOVER_COLOR;
    /** Mockup orange accent for title / next event. */
    private static final Color ACCENT_COLOR = new Color(232, 140, 36);
    /** Live / happening now. */
    private static final Color[] TONE_LIVE = {
        new Color(72, 196, 88),
        new Color(96, 210, 110),
        new Color(52, 170, 78),
        new Color(120, 220, 140),
    };
    /** Today, not currently live. */
    private static final Color[] TONE_TODAY = {
        new Color(232, 140, 36),
        new Color(240, 168, 64),
        new Color(210, 118, 28),
        new Color(246, 188, 96),
    };
    /** Upcoming (future days). */
    private static final Color[] TONE_FUTURE = {
        new Color(70, 168, 236),
        new Color(100, 186, 246),
        new Color(50, 140, 210),
        new Color(130, 200, 250),
    };
    /** Already ended. */
    private static final Color[] TONE_PAST = {
        new Color(140, 140, 140),
        new Color(120, 120, 120),
        new Color(158, 158, 158),
        new Color(108, 108, 108),
    };
    private static final Color LINK_COLOR = new Color(100, 180, 255);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color SECONDARY_TEXT = ColorScheme.LIGHT_GRAY_COLOR;
    private static final Color MUTED_TEXT = new Color(140, 140, 140);
    private static final Color RAIL_COLOR = new Color(90, 90, 90);

    private enum EventTone
    {
        LIVE,
        TODAY,
        FUTURE,
        PAST
    }

    private static final int RAIL_WIDTH = 14;
    private static final int NODE_SIZE = 8;
    private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH;
    private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - RAIL_WIDTH - 20;
    private static final int SIDE_PAD = 6;
    private static final int MAX_DESC_LENGTH = 280;

    private final EventListConfig config;
    private final ConfigManager configManager;
    private final EventsApiService eventsApiService;
    private final EventTeamService eventTeamService;
    private final EventTeamSyncService eventTeamSyncService;
    private final Runnable refreshCallback;
    private final Runnable syncTeamsCallback;
    private final Runnable openBoardCallback;
    private JPanel seasonalEventCard;

    private JPanel eventsContainer;
    private JScrollPane eventsScrollPane;
    private JScrollPane seasonalScrollPane;
    private JLabel myTeamEventLabel;
    private JLabel myTeamTitleLabel;
    private JPanel myTeamMembers;
    private JLabel myTeamFooterLabel;
    private JLabel teamsConflictLabel;
    private JLabel teamsSyncLabel;
    private JLabel teamsHeaderLabel;
    private JPanel teamsContainer;
    private final Map<Integer, JLabel> teamLabels = new HashMap<>();
    private final Map<Integer, JTextArea> teamAreas = new HashMap<>();
    private JSpinner teamCountSpinner;
    private JLabel statusLabel;
    private JLabel lastUpdateLabel;
    private HealthDot healthDot;
    private JTabbedPane tabs;

    /** Identity of the expanded timeline row, or null. */
    private String expandedEventKey;

    /** Viewport width used when rows were last built; triggers rebuild when it changes. */
    private int laidOutViewportWidth;

    public EventListPanel(
        EventListConfig config,
        ConfigManager configManager,
        EventsApiService eventsApiService,
        EventTeamService eventTeamService,
        EventTeamSyncService eventTeamSyncService,
        Runnable refreshCallback,
        Runnable syncTeamsCallback,
        Runnable openBoardCallback
    )
    {
        super(false);

        this.config = config;
        this.configManager = configManager;
        this.eventsApiService = eventsApiService;
        this.eventTeamService = eventTeamService;
        this.eventTeamSyncService = eventTeamSyncService;
        this.refreshCallback = refreshCallback;
        this.syncTeamsCallback = syncTeamsCallback;
        this.openBoardCallback = openBoardCallback;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);

        add(createHeaderPanel(), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.setBackground(BACKGROUND_COLOR);
        tabs.setForeground(TEXT_COLOR);
        tabs.addTab("Events", createEventsTab());
        tabs.addTab("Seasonal", createSeasonalTab());
        add(tabs, BorderLayout.CENTER);
        add(createFooterPanel(), BorderLayout.SOUTH);

        refreshHealthDot();
        rebuildTeamsUi();
    }

    private JPanel createEventsTab()
    {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(BACKGROUND_COLOR);
        tab.add(createEventsScrollPane(), BorderLayout.CENTER);
        return tab;
    }

    private JPanel createSeasonalTab()
    {
        // Same width-tracking pattern as Events — otherwise BoxLayout children overflow and clip.
        JPanel body = new JPanel()
        {
            @Override
            public Dimension getPreferredSize()
            {
                Dimension pref = super.getPreferredSize();
                return new Dimension(seasonalViewportWidth(), pref.height);
            }

            @Override
            public Dimension getMinimumSize()
            {
                return new Dimension(0, super.getMinimumSize().height);
            }

            @Override
            public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
        };
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BACKGROUND_COLOR);
        body.setBorder(new EmptyBorder(8, SIDE_PAD, 8, SIDE_PAD));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        seasonalEventCard = new JPanel();
        seasonalEventCard.setLayout(new BoxLayout(seasonalEventCard, BoxLayout.Y_AXIS));
        seasonalEventCard.setBackground(PANEL_COLOR);
        seasonalEventCard.setBorder(new EmptyBorder(8, 8, 8, 8));
        constrainSeasonalSection(seasonalEventCard);
        seasonalEventCard.setVisible(false);
        body.add(seasonalEventCard);
        body.add(Box.createVerticalStrut(12));
        body.add(createMyTeamCard());
        body.add(Box.createVerticalStrut(12));
        body.add(createSeasonalTeamsSection());

        seasonalScrollPane = new JScrollPane(new ScrollableWidthPanel(body));
        seasonalScrollPane.setBorder(BorderFactory.createEmptyBorder());
        seasonalScrollPane.setBackground(BACKGROUND_COLOR);
        seasonalScrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        seasonalScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        seasonalScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        seasonalScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(BACKGROUND_COLOR);
        tab.add(seasonalScrollPane, BorderLayout.CENTER);
        return tab;
    }

    private int seasonalViewportWidth()
    {
        if (seasonalScrollPane != null)
        {
            int w = seasonalScrollPane.getViewport().getWidth();
            if (w > 0)
            {
                return w;
            }
        }
        return Math.max(1, PluginPanel.PANEL_WIDTH);
    }

    private void constrainSeasonalSection(JPanel section)
    {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMinimumSize(new Dimension(0, 1));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    /** EDT. Hidden when v is null (setting off or nothing fetched yet). */
    public void updateSeasonalEventCard(SeasonalView v)
    {
        if (seasonalEventCard == null)
        {
            return;
        }
        seasonalEventCard.removeAll();
        if (v == null || !config.showSeasonalEvent())
        {
            seasonalEventCard.setVisible(false);
            return;
        }
        if (v.event != null)
        {
            seasonalEventCard.add(seasonalLabel(v.event.name == null ? "Seasonal event" : v.event.name,
                FontManager.getRunescapeBoldFont(), ACCENT_COLOR));
            seasonalEventCard.add(seasonalLabel(seasonalTiming(v.event), FontManager.getRunescapeSmallFont(), MUTED_TEXT));
        }
        if (v.team != null && v.team.name != null)
        {
            JLabel team = seasonalLabel(v.team.name, FontManager.getRunescapeBoldFont(), TEXT_COLOR);
            team.setBorder(new EmptyBorder(6, 0, 4, 0));
            seasonalEventCard.add(team);
        }
        if (v.summary != null)
        {
            for (SeasonalView.Stat s : v.summary)
            {
                seasonalEventCard.add(seasonalLabel(s.label + ": " + s.value, FontManager.getRunescapeSmallFont(), TEXT_COLOR));
            }
        }
        if (v.message != null && !v.message.isEmpty())
        {
            seasonalEventCard.add(seasonalLabel(v.message, FontManager.getRunescapeSmallFont(), MUTED_TEXT));
        }
        if (v.pieces != null && !v.pieces.isEmpty())
        {
            JButton open = new JButton("Open board");
            open.setAlignmentX(Component.LEFT_ALIGNMENT);
            open.addActionListener(e -> openBoardCallback.run());
            seasonalEventCard.add(Box.createVerticalStrut(6));
            seasonalEventCard.add(open);
        }
        seasonalEventCard.setVisible(true);
        seasonalEventCard.revalidate();
        seasonalEventCard.repaint();
    }

    private static JLabel seasonalLabel(String text, Font font, Color color)
    {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String seasonalTiming(SeasonalView.Event e)
    {
        try
        {
            if ("live".equals(e.status) && e.endsAt != null)
            {
                java.time.Duration d = java.time.Duration.between(Instant.now(), Instant.parse(e.endsAt));
                return d.isNegative() ? "Ended" : d.toDays() + "d " + (d.toHours() % 24) + "h left";
            }
            if ("upcoming".equals(e.status) && e.startsAt != null)
            {
                return "Starts " + java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")
                    .withZone(java.time.ZoneId.systemDefault()).format(Instant.parse(e.startsAt));
            }
        }
        catch (java.time.DateTimeException ex)
        {
            // malformed date: fall through
        }
        return "Ended";
    }

    private JPanel createMyTeamCard()
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(PANEL_COLOR);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        constrainSeasonalSection(card);

        JLabel heading = new JLabel("Your team");
        heading.setFont(FontManager.getRunescapeBoldFont());
        heading.setForeground(ACCENT_COLOR);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(heading);

        myTeamEventLabel = new JLabel(" ");
        myTeamEventLabel.setFont(FontManager.getRunescapeSmallFont());
        myTeamEventLabel.setForeground(MUTED_TEXT);
        myTeamEventLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(myTeamEventLabel);

        myTeamTitleLabel = new JLabel(" ");
        myTeamTitleLabel.setFont(FontManager.getRunescapeBoldFont());
        myTeamTitleLabel.setForeground(TEXT_COLOR);
        myTeamTitleLabel.setIconTextGap(6);
        myTeamTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        myTeamTitleLabel.setBorder(new EmptyBorder(6, 0, 4, 0));
        card.add(myTeamTitleLabel);

        myTeamMembers = new JPanel();
        myTeamMembers.setLayout(new BoxLayout(myTeamMembers, BoxLayout.Y_AXIS));
        myTeamMembers.setOpaque(false);
        myTeamMembers.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(myTeamMembers);

        myTeamFooterLabel = new JLabel(" ");
        myTeamFooterLabel.setFont(FontManager.getRunescapeSmallFont());
        myTeamFooterLabel.setForeground(MUTED_TEXT);
        myTeamFooterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        myTeamFooterLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
        card.add(myTeamFooterLabel);

        return card;
    }

    /** The slot whose roster lists the local player, or 0. */
    private int findMyTeam(String localName, int teamCount)
    {
        if (localName.isEmpty())
        {
            return 0;
        }
        for (int team = 1; team <= teamCount; team++)
        {
            for (String name : teamMembers(team))
            {
                if (localName.equals(Text.standardize(name)))
                {
                    return team;
                }
            }
        }
        return 0;
    }

    private List<String> teamMembers(int team)
    {
        return EventTeamService.splitNames(configManager.getConfiguration(
            EventListConfig.CONFIG_GROUP, EventTeamService.teamNamesKey(team)));
    }

    private String teamTitle(int team)
    {
        String labelName = configManager.getConfiguration(
            EventListConfig.CONFIG_GROUP, EventTeamService.teamLabelKey(team));
        return (labelName == null || labelName.trim().isEmpty()) ? ("Team " + team) : labelName.trim();
    }

    private void updateMyTeamCard(EventTeamService.RosterStatus status, int teamCount)
    {
        if (myTeamMembers == null)
        {
            return;
        }
        myTeamMembers.removeAll();
        myTeamTitleLabel.setIcon(null);

        String eventName = eventTeamSyncService.getEventName();
        myTeamEventLabel.setText(eventName.isEmpty() ? "Seasonal event" : eventName);
        myTeamFooterLabel.setText(rosterFooter());

        boolean anyRoster = false;
        for (int team = 1; team <= teamCount && !anyRoster; team++)
        {
            anyRoster = !teamMembers(team).isEmpty();
        }

        int myTeam = findMyTeam(status.localName, teamCount);
        if (!anyRoster)
        {
            myTeamTitleLabel.setText("<html>No team roster yet. Turn on Sync team roster from API in plugin config.</html>");
        }
        else if (status.localName.isEmpty())
        {
            myTeamTitleLabel.setText("Log in to see your team.");
        }
        else if (myTeam == 0)
        {
            myTeamTitleLabel.setText("<html>You're not on a team for this event.</html>");
        }
        else
        {
            List<String> members = teamMembers(myTeam);
            int online = 0;
            for (String name : members)
            {
                boolean isOnline = status.onlineNames.contains(Text.standardize(name));
                boolean isMe = status.localName.equals(Text.standardize(name));
                online += isOnline ? 1 : 0;
                myTeamMembers.add(createMemberRow(name, isOnline, isMe));
            }
            BufferedImage badge = eventTeamService.paletteBadge(eventTeamService.teamPalette(myTeam));
            myTeamTitleLabel.setIcon(badge == null ? null : new ImageIcon(badge));
            myTeamTitleLabel.setText(teamTitle(myTeam) + " \u00b7 " + online + "/" + members.size() + " online");
        }

        myTeamMembers.revalidate();
        myTeamMembers.repaint();
    }

    private JLabel createMemberRow(String name, boolean online, boolean isMe)
    {
        JLabel row = new JLabel("\u25cf " + name + (isMe ? " (you)" : ""));
        row.setFont(FontManager.getRunescapeSmallFont());
        row.setForeground(online ? TONE_LIVE[0] : MUTED_TEXT);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(1, 4, 1, 0));
        return row;
    }

    private String rosterFooter()
    {
        if (!config.enableTeamRosterSync())
        {
            return "Roster set by hand (API sync off)";
        }
        String updatedAt = eventTeamSyncService.getRosterUpdatedAt();
        if (updatedAt.isEmpty())
        {
            return eventTeamSyncService.getLastStatus();
        }
        try
        {
            return "Roster updated " + new SimpleDateFormat("MMM d, HH:mm").format(Date.from(Instant.parse(updatedAt)));
        }
        catch (RuntimeException e)
        {
            return "Roster updated " + updatedAt;
        }
    }

    private JPanel createSeasonalTeamsSection()
    {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        constrainSeasonalSection(section);

        // Collapsed by default (per session): the header carries the team/online summary,
        // the editing controls live in the body.
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setVisible(false);

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        teamsHeaderLabel = new JLabel("Event teams");
        teamsHeaderLabel.setFont(FontManager.getRunescapeBoldFont());
        teamsHeaderLabel.setForeground(ACCENT_COLOR);
        header.add(teamsHeaderLabel, BorderLayout.CENTER);
        JLabel chevron = new JLabel("▸");
        chevron.setFont(FontManager.getRunescapeSmallFont());
        chevron.setForeground(MUTED_TEXT);
        header.add(chevron, BorderLayout.EAST);
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                boolean open = !body.isVisible();
                body.setVisible(open);
                chevron.setText(open ? "▾" : "▸");
                section.revalidate();
                section.repaint();
            }
        });
        section.add(header);
        section.add(body);

        JLabel hint = new JLabel("<html>Paste RSNs per team (comma or newline). "
            + "Supports # / // comments. Click the clan icon to cycle colors. "
            + "Icons show in chat, PMs, friends list, and clan list when Team icons is enabled.</html>");
        hint.setFont(FontManager.getRunescapeSmallFont());
        hint.setForeground(MUTED_TEXT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(4, 0, 6, 0));
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        body.add(hint);

        JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        countRow.setOpaque(false);
        countRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel countLabel = new JLabel("Teams");
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(TEXT_COLOR);
        countRow.add(countLabel);

        int count = Math.min(Math.max(config.teamCount(), 1), EventTeamService.MAX_TEAMS);
        teamCountSpinner = new JSpinner(new SpinnerNumberModel(count, 1, EventTeamService.MAX_TEAMS, 1));
        teamCountSpinner.setPreferredSize(new Dimension(56, 22));
        teamCountSpinner.addChangeListener(e ->
        {
            int value = (Integer) teamCountSpinner.getValue();
            // ConfigChanged("teamCount") rebuilds the team sections.
            configManager.setConfiguration(EventListConfig.CONFIG_GROUP, "teamCount", value);
        });
        countRow.add(teamCountSpinner);
        body.add(countRow);

        JPanel syncRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        syncRow.setOpaque(false);
        syncRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncRow.add(createPanelButton("Sync teams", "Fetch roster from GET /plugins/seasonal/teams", e ->
        {
            if (syncTeamsCallback != null)
            {
                syncTeamsCallback.run();
            }
        }));
        body.add(syncRow);

        teamsSyncLabel = new JLabel(" ");
        teamsSyncLabel.setFont(FontManager.getRunescapeSmallFont());
        teamsSyncLabel.setForeground(SECONDARY_TEXT);
        teamsSyncLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        teamsSyncLabel.setBorder(new EmptyBorder(2, 0, 2, 0));
        body.add(teamsSyncLabel);

        teamsConflictLabel = new JLabel(" ");
        teamsConflictLabel.setFont(FontManager.getRunescapeSmallFont());
        teamsConflictLabel.setForeground(new Color(220, 120, 80));
        teamsConflictLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        teamsConflictLabel.setBorder(new EmptyBorder(2, 0, 6, 0));
        body.add(teamsConflictLabel);

        teamsContainer = new JPanel();
        teamsContainer.setLayout(new BoxLayout(teamsContainer, BoxLayout.Y_AXIS));
        teamsContainer.setOpaque(false);
        constrainSeasonalSection(teamsContainer);
        body.add(teamsContainer);

        return section;
    }

    private JPanel createHeaderPanel()
    {
        JPanel headerWrapper = new JPanel();
        headerWrapper.setLayout(new BoxLayout(headerWrapper, BoxLayout.Y_AXIS));
        headerWrapper.setBackground(BACKGROUND_COLOR);
        headerWrapper.setBorder(new EmptyBorder(10, SIDE_PAD, 10, SIDE_PAD));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setBackground(BACKGROUND_COLOR);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        healthDot = new HealthDot();
        titleRow.add(healthDot);

        JLabel titleLabel = new JLabel("Ironforged");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
        titleLabel.setForeground(ACCENT_COLOR);
        titleRow.add(titleLabel);

        titleBlock.add(titleRow);

        JLabel subtitleLabel = new JLabel("Clan Events");
        subtitleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
        subtitleLabel.setForeground(TEXT_COLOR);
        subtitleLabel.setBorder(new EmptyBorder(2, 18, 0, 0));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock.add(subtitleLabel);

        topRow.add(titleBlock, BorderLayout.WEST);

        JButton refreshButton = new JButton("↻");
        refreshButton.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        refreshButton.setForeground(TEXT_COLOR);
        refreshButton.setBackground(PANEL_COLOR);
        refreshButton.setBorder(new EmptyBorder(5, 10, 5, 10));
        refreshButton.setFocusPainted(false);
        refreshButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshButton.setToolTipText("Refresh events from Iron Forged API");
        refreshButton.addActionListener(e ->
        {
            if (refreshCallback != null)
            {
                refreshCallback.run();
            }
        });
        refreshButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                refreshButton.setBackground(HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                refreshButton.setBackground(PANEL_COLOR);
            }
        });
        topRow.add(refreshButton, BorderLayout.EAST);

        headerWrapper.add(topRow);
        return headerWrapper;
    }

    private JButton createPanelButton(String label, String tooltip, java.awt.event.ActionListener action)
    {
        JButton button = new JButton(label);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(TEXT_COLOR);
        button.setBackground(BACKGROUND_COLOR);
        button.setBorder(new EmptyBorder(4, 8, 4, 8));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(tooltip);
        button.addActionListener(action);
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                button.setBackground(HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(BACKGROUND_COLOR);
            }
        });
        return button;
    }

    public void refreshTeamsOnlineCounts()
    {
        if (eventTeamService == null)
        {
            return;
        }
        eventTeamService.computeOnlineCounts(status ->
        {
            int teamCount = Math.min(Math.max(config.teamCount(), 1), EventTeamService.MAX_TEAMS);
            int onlineTotal = 0;
            for (int team = 1; team <= teamCount; team++)
            {
                onlineTotal += updateTeamLabel(team, status.onlineByTeam.getOrDefault(team, 0));
            }
            updateMyTeamCard(status, teamCount);
            if (teamsHeaderLabel != null)
            {
                teamsHeaderLabel.setText("Event teams · " + teamCount + " teams · " + onlineTotal + " online");
            }
            List<String> conflicts = eventTeamService.getConflicts();
            if (teamsConflictLabel != null)
            {
                if (conflicts.isEmpty())
                {
                    teamsConflictLabel.setText(" ");
                }
                else
                {
                    teamsConflictLabel.setText("<html>" + conflicts.size() + " duplicate name(s): "
                        + escapeHtml(String.join("; ", conflicts.subList(0, Math.min(3, conflicts.size()))))
                        + (conflicts.size() > 3 ? "…" : "")
                        + "</html>");
                }
            }
        });
    }

    public void rebuildTeamsUi()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (teamsContainer == null)
            {
                return;
            }
            teamsContainer.removeAll();
            teamLabels.clear();
            teamAreas.clear();

            int teamCount = Math.min(Math.max(config.teamCount(), 1), EventTeamService.MAX_TEAMS);
            if (teamCountSpinner != null && !Objects.equals(teamCountSpinner.getValue(), teamCount))
            {
                teamCountSpinner.setValue(teamCount);
            }

            for (int team = 1; team <= teamCount; team++)
            {
                teamsContainer.add(createTeamSection(team));
                teamsContainer.add(Box.createVerticalStrut(8));
            }
            teamsContainer.revalidate();
            teamsContainer.repaint();
            updateTeamsSyncLabel();
            refreshTeamsOnlineCounts();
        });
    }

    public void updateTeamsSyncLabel()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (teamsSyncLabel == null || eventTeamSyncService == null)
            {
                return;
            }
            String status = eventTeamSyncService.getLastStatus();
            if (!config.enableTeamRosterSync())
            {
                teamsSyncLabel.setText("API sync off — enable in plugin config");
            }
            else
            {
                teamsSyncLabel.setText(status == null ? " " : status);
            }
        });
    }

    /**
     * Pulls saved team names into the existing text areas without rebuilding them,
     * skipping the one being typed in so the caret and focus are kept.
     */
    public void refreshTeamNames()
    {
        SwingUtilities.invokeLater(() ->
        {
            for (Map.Entry<Integer, JTextArea> entry : teamAreas.entrySet())
            {
                JTextArea area = entry.getValue();
                String saved = Objects.toString(configManager.getConfiguration(
                    EventListConfig.CONFIG_GROUP, EventTeamService.teamNamesKey(entry.getKey())), "");
                if (!area.isFocusOwner() && !saved.equals(area.getText()))
                {
                    area.setText(saved);
                }
            }
            refreshTeamsOnlineCounts();
        });
    }

    private JPanel createTeamSection(int team)
    {
        JPanel section = new JPanel(new BorderLayout(0, 4));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        JLabel label = new JLabel();
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(TEXT_COLOR);
        header.add(label, BorderLayout.CENTER);
        header.add(createTeamColorButton(team), BorderLayout.EAST);
        section.add(header, BorderLayout.NORTH);

        JTextArea namesArea = new JTextArea(4, 20);
        namesArea.setLineWrap(true);
        namesArea.setWrapStyleWord(true);
        namesArea.setFont(FontManager.getRunescapeSmallFont());
        namesArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        namesArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        namesArea.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
        namesArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(4, 4, 4, 4)));

        String saved = configManager.getConfiguration(EventListConfig.CONFIG_GROUP, EventTeamService.teamNamesKey(team));
        if (saved != null)
        {
            namesArea.setText(saved);
        }

        teamLabels.put(team, label);
        teamAreas.put(team, namesArea);
        updateTeamLabel(team, 0);

        Timer saveTimer = new Timer(500, e ->
            configManager.setConfiguration(
                EventListConfig.CONFIG_GROUP,
                EventTeamService.teamNamesKey(team),
                namesArea.getText()));
        saveTimer.setRepeats(false);

        namesArea.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                changed();
            }

            private void changed()
            {
                updateTeamLabel(team, 0);
                saveTimer.restart();
            }
        });

        section.add(namesArea, BorderLayout.CENTER);
        return section;
    }

    /** Returns the online count shown (capped at roster size) so the header can total it. */
    private int updateTeamLabel(int team, int online)
    {
        JLabel label = teamLabels.get(team);
        JTextArea namesArea = teamAreas.get(team);
        if (label == null || namesArea == null)
        {
            return 0;
        }
        int total = EventTeamService.splitNames(namesArea.getText()).size();
        int shownOnline = Math.min(online, total);
        String labelName = configManager.getConfiguration(
            EventListConfig.CONFIG_GROUP, EventTeamService.teamLabelKey(team));
        String title = (labelName == null || labelName.trim().isEmpty())
            ? ("Team " + team)
            : labelName.trim();
        label.setText(title + " — " + shownOnline + "/" + total + " online");
        return shownOnline;
    }

    private JButton createTeamColorButton(int team)
    {
        JButton colorButton = new JButton();
        colorButton.setPreferredSize(new Dimension(22, 22));
        colorButton.setContentAreaFilled(false);
        colorButton.setOpaque(false);
        colorButton.setBorderPainted(true);
        colorButton.setRolloverEnabled(false);
        colorButton.setFocusable(false);
        colorButton.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        applyTeamColorButton(colorButton, eventTeamService.teamPalette(team));
        colorButton.addActionListener(e ->
        {
            eventTeamService.cycleTeamPalette(team);
            applyTeamColorButton(colorButton, eventTeamService.teamPalette(team));
        });
        return colorButton;
    }

    private void applyTeamColorButton(JButton colorButton, EventTeamIconFactory.TeamPalette palette)
    {
        BufferedImage badge = eventTeamService.paletteBadge(palette);
        if (badge != null)
        {
            colorButton.setIcon(new ImageIcon(badge));
        }
        // Neutral chrome — never paint the team hue behind the icon (red-on-red / gold-on-gold).
        colorButton.setBackground(PANEL_COLOR);
        colorButton.setToolTipText(palette.getLabel());
    }

    private void refreshHealthDot()
    {
        if (healthDot == null)
        {
            return;
        }
        ApiStatus status = eventsApiService.getApiStatus();
        healthDot.setStatus(status);
    }

    private JScrollPane createEventsScrollPane()
    {
        // Track viewport width so rows fill the panel on first layout (not only after a click rebuild).
        eventsContainer = new JPanel()
        {
            @Override
            public Dimension getPreferredSize()
            {
                Dimension pref = super.getPreferredSize();
                return new Dimension(viewportWidth(), pref.height);
            }

            @Override
            public Dimension getMinimumSize()
            {
                return new Dimension(viewportWidth(), super.getMinimumSize().height);
            }

            @Override
            public Dimension getMaximumSize()
            {
                return new Dimension(Short.MAX_VALUE, Integer.MAX_VALUE);
            }

            @Override
            public boolean isOptimizedDrawingEnabled()
            {
                return true;
            }
        };
        eventsContainer.setLayout(new BoxLayout(eventsContainer, BoxLayout.Y_AXIS));
        eventsContainer.setBackground(BACKGROUND_COLOR);
        eventsContainer.setBorder(new EmptyBorder(0, SIDE_PAD, 0, SIDE_PAD));
        eventsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Force the view to always match viewport width (avoids centered narrow first paint).
        eventsScrollPane = new JScrollPane(new ScrollableWidthPanel(eventsContainer));
        eventsScrollPane.setBackground(BACKGROUND_COLOR);
        eventsScrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        eventsScrollPane.setBorder(null);
        eventsScrollPane.setViewportBorder(null);
        eventsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        eventsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        eventsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        eventsScrollPane.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                maybeRelayoutForViewportWidth();
            }
        });
        eventsScrollPane.getViewport().addChangeListener(e -> maybeRelayoutForViewportWidth());
        return eventsScrollPane;
    }

    private void maybeRelayoutForViewportWidth()
    {
        int w = eventsScrollPane != null ? eventsScrollPane.getViewport().getWidth() : 0;
        if (w <= 0 || w == laidOutViewportWidth)
        {
            return;
        }
        // Rebuild once the viewport has a real width (first show / resize).
        updateEvents();
    }

    private int viewportWidth()
    {
        if (eventsScrollPane != null)
        {
            int w = eventsScrollPane.getViewport().getWidth();
            if (w > 0)
            {
                return w;
            }
        }
        // Prefer full panel width before scrollbar appears; scrollbar is accounted for after layout.
        return Math.max(1, PluginPanel.PANEL_WIDTH);
    }

    /** Usable row width inside side padding. */
    private int contentWidth()
    {
        return Math.max(1, viewportWidth() - (SIDE_PAD * 2));
    }

    /**
     * Viewport view that always tracks viewport width so children stretch on first paint.
     */
    private static final class ScrollableWidthPanel extends JPanel implements Scrollable
    {
        private ScrollableWidthPanel(JComponent content)
        {
            setLayout(new BorderLayout());
            setBackground(BACKGROUND_COLOR);
            add(content, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private JPanel createFooterPanel()
    {
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(BACKGROUND_COLOR);
        footerPanel.setBorder(new EmptyBorder(8, SIDE_PAD, 8, SIDE_PAD));

        statusLabel = new JLabel("Loading...");
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(SECONDARY_TEXT);
        footerPanel.add(statusLabel, BorderLayout.WEST);

        lastUpdateLabel = new JLabel("");
        lastUpdateLabel.setFont(FontManager.getRunescapeSmallFont());
        lastUpdateLabel.setForeground(SECONDARY_TEXT);
        footerPanel.add(lastUpdateLabel, BorderLayout.EAST);

        return footerPanel;
    }

    /**
     * Updates the panel with the latest events.
     */
    public void updateEvents()
    {
        SwingUtilities.invokeLater(() ->
        {
            // Lock current viewport width so resize listener does not loop.
            laidOutViewportWidth = viewportWidth();

            eventsContainer.removeAll();

            List<ClanEvent> events;
            if (config.showPastEvents())
            {
                events = eventsApiService.getCachedEvents();
            }
            else
            {
                events = eventsApiService.getUpcomingEvents();
            }

            int maxEvents = config.maxEventsInPanel();

            if (events.isEmpty())
            {
                JLabel noEventsLabel = new JLabel("No upcoming events");
                noEventsLabel.setFont(FontManager.getRunescapeFont());
                noEventsLabel.setForeground(SECONDARY_TEXT);
                noEventsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                noEventsLabel.setBorder(new EmptyBorder(20, 0, 10, 0));
                eventsContainer.add(noEventsLabel);

                if (!config.enableEventsFeed())
                {
                    JLabel configLabel = new JLabel(
                        "<html><center>Enable <b>Load clan events</b><br>in the plugin settings</center></html>");
                    configLabel.setFont(FontManager.getRunescapeSmallFont());
                    configLabel.setForeground(SECONDARY_TEXT);
                    configLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    eventsContainer.add(configLabel);
                }
            }
            else
            {
                ClanEvent activeEvent = eventsApiService.getActiveEvent();
                ClanEvent highlightedEvent = eventsApiService.getHighlightedEvent();
                LocalDate today = LocalDate.now();

                List<ClanEvent> weekly = new ArrayList<>();
                Map<LocalDate, List<ClanEvent>> byDay = new LinkedHashMap<>();
                int listed = 0;
                int hidden = 0;

                // Ensure today's block exists first when a live non-weekly event is active
                if (activeEvent != null
                    && activeEvent.getLocalDate() != null
                    && !activeEvent.isWeeklySpotlight())
                {
                    byDay.computeIfAbsent(activeEvent.getLocalDate(), d -> new ArrayList<>());
                    byDay.get(activeEvent.getLocalDate()).add(activeEvent);
                }
                else if (activeEvent != null && activeEvent.isWeeklySpotlight())
                {
                    weekly.add(activeEvent);
                }

                for (ClanEvent event : events)
                {
                    if (activeEvent != null && event.equals(activeEvent))
                    {
                        continue;
                    }

                    LocalDate day = event.getLocalDate();
                    if (day == null)
                    {
                        continue;
                    }

                    boolean pastDay = day.isBefore(today);
                    if (pastDay && !config.showPastEvents() && !event.isWeeklySpotlight())
                    {
                        continue;
                    }
                    if (event.isWeeklySpotlight() && event.isPast() && !config.showPastEvents())
                    {
                        continue;
                    }

                    if (listed >= maxEvents)
                    {
                        hidden++;
                        continue;
                    }
                    listed++;

                    if (event.isWeeklySpotlight())
                    {
                        weekly.add(event);
                    }
                    else
                    {
                        byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(event);
                    }
                }

                Collections.sort(weekly);

                boolean firstRow = true;
                if (!weekly.isEmpty())
                {
                    boolean weeklyPast = weekly.stream().allMatch(ClanEvent::isPast);
                    eventsContainer.add(createTimelineDayMarker(
                        "WEEKLY · SOTW / BOTW",
                        weeklyPast,
                        false,
                        true));
                    firstRow = false;

                    for (int i = 0; i < weekly.size(); i++)
                    {
                        ClanEvent event = weekly.get(i);
                        boolean isLive = activeEvent != null && event.equals(activeEvent);
                        boolean isNext = !isLive
                            && activeEvent == null
                            && highlightedEvent != null
                            && event.equals(highlightedEvent);
                        boolean past = event.isPast();
                        boolean lastOverall = byDay.isEmpty() && i == weekly.size() - 1;
                        int toneVariant = toneVariantInList(weekly, i);
                        eventsContainer.add(createTimelineEventRow(
                            event, isLive, isNext, past, toneVariant, true, !lastOverall));
                        if (!lastOverall)
                        {
                            eventsContainer.add(Box.createVerticalStrut(2));
                        }
                    }
                }

                List<LocalDate> sortedDays = new ArrayList<>(byDay.keySet());
                Collections.sort(sortedDays);

                for (int dayIndex = 0; dayIndex < sortedDays.size(); dayIndex++)
                {
                    LocalDate day = sortedDays.get(dayIndex);
                    List<ClanEvent> dayEvents = byDay.get(day);
                    boolean pastDay = day.isBefore(today);
                    boolean lastDay = dayIndex == sortedDays.size() - 1;

                    eventsContainer.add(createTimelineDayMarker(
                        dayEvents.get(0).getDayBlockHeader(),
                        pastDay,
                        !firstRow,
                        true));
                    firstRow = false;

                    for (int i = 0; i < dayEvents.size(); i++)
                    {
                        ClanEvent event = dayEvents.get(i);
                        boolean isLive = activeEvent != null && event.equals(activeEvent);
                        boolean isNext = !isLive
                            && activeEvent == null
                            && highlightedEvent != null
                            && event.equals(highlightedEvent);
                        boolean lastEvent = lastDay && i == dayEvents.size() - 1;
                        int toneVariant = toneVariantInList(dayEvents, i);
                        eventsContainer.add(createTimelineEventRow(
                            event, isLive, isNext, pastDay, toneVariant, true, !lastEvent));
                        if (!lastEvent)
                        {
                            eventsContainer.add(Box.createVerticalStrut(2));
                        }
                    }
                }

                if (hidden > 0)
                {
                    JLabel moreLabel = new JLabel("+" + hidden + " more");
                    moreLabel.setFont(FontManager.getRunescapeSmallFont());
                    moreLabel.setForeground(SECONDARY_TEXT);
                    moreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    moreLabel.setBorder(new EmptyBorder(6, RAIL_WIDTH + 4, 0, 0));
                    eventsContainer.add(moreLabel);
                }
            }

            eventsContainer.add(Box.createVerticalGlue());

            statusLabel.setText(events.size() + " event" + (events.size() != 1 ? "s" : ""));

            long lastFetch = eventsApiService.getLastFetchTime();
            if (lastFetch > 0)
            {
                long minutesAgo = (System.currentTimeMillis() - lastFetch) / 60000;
                lastUpdateLabel.setText(minutesAgo < 1 ? "Just now" : minutesAgo + "m ago");
            }
            else
            {
                lastUpdateLabel.setText("Not synced");
            }

            refreshHealthDot();

            eventsContainer.revalidate();
            eventsContainer.repaint();
        });
    }

    private JPanel createTimelineDayMarker(String title, boolean muted, boolean connectTop, boolean connectBottom)
    {
        final int rowHeight = 30;
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(BACKGROUND_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMinimumSize(new Dimension(0, rowHeight));
        row.setPreferredSize(new Dimension(contentWidth(), rowHeight));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
        row.setBorder(new EmptyBorder(8, 0, 2, 0));

        RailSegment rail = new RailSegment(null, 0, connectTop, connectBottom);
        rail.setPreferredSize(new Dimension(RAIL_WIDTH, rowHeight));
        row.add(rail, BorderLayout.WEST);

        Color dateColor = muted ? MUTED_TEXT : SECONDARY_TEXT;

        JPanel dateBlock = new JPanel(new BorderLayout(5, 0));
        dateBlock.setOpaque(false);
        dateBlock.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(2, 0, 0, 0),
            BorderFactory.createMatteBorder(0, 0, 1, 0, muted ? RAIL_COLOR : new Color(120, 120, 120))
        ));

        JComponent calIcon = new CalendarGlyph(dateColor);
        calIcon.setPreferredSize(new Dimension(11, 12));
        calIcon.setMinimumSize(new Dimension(11, 12));
        dateBlock.add(calIcon, BorderLayout.WEST);

        JLabel titleLabel = new JLabel(plainLabelText(title));
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(dateColor);
        titleLabel.setBorder(new EmptyBorder(1, 0, 3, 0));
        dateBlock.add(titleLabel, BorderLayout.CENTER);

        row.add(dateBlock, BorderLayout.CENTER);
        return row;
    }

    /**
     * Small status circle next to the panel title. Hover shows API health details.
     */
    private static final class HealthDot extends JComponent
    {
        private static final Color UNKNOWN = new Color(140, 140, 140);
        private static final Color HEALTHY = new Color(72, 196, 88);
        private static final Color DEGRADED = new Color(232, 180, 36);
        private static final Color DOWN = new Color(220, 72, 72);

        private Color fill = UNKNOWN;

        private HealthDot()
        {
            setOpaque(false);
            setPreferredSize(new Dimension(12, 12));
            setMinimumSize(new Dimension(12, 12));
            setMaximumSize(new Dimension(12, 12));
            setToolTipText("No API status yet");
        }

        void setStatus(ApiStatus status)
        {
            if (status == null)
            {
                fill = UNKNOWN;
                setToolTipText("No API status yet");
            }
            else
            {
                switch (status.getLevel())
                {
                    case HEALTHY:
                        fill = HEALTHY;
                        break;
                    case DEGRADED:
                        fill = DEGRADED;
                        break;
                    case DOWN:
                        fill = DOWN;
                        break;
                    case UNKNOWN:
                    default:
                        fill = UNKNOWN;
                        break;
                }
                setToolTipText(status.getTooltip());
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                g2.setColor(fill);
                g2.fillOval(x, y, size, size);
                g2.setColor(new Color(0, 0, 0, 80));
                g2.drawOval(x, y, size, size);
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    /** Tiny calendar glyph for day headers. */
    private static final class CalendarGlyph extends JComponent
    {
        private final Color color;

        private CalendarGlyph(Color color)
        {
            this.color = color;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                int w = Math.min(getWidth(), 11);
                int h = Math.min(getHeight(), 11);
                int x = Math.max(0, (getWidth() - w) / 2);
                int y = Math.max(0, (getHeight() - h) / 2);

                g2.setColor(color);
                // Binding rings
                g2.fillRect(x + 2, y, 2, 3);
                g2.fillRect(x + w - 4, y, 2, 3);
                // Body
                g2.drawRect(x, y + 2, w - 1, h - 3);
                g2.fillRect(x, y + 2, w, 2);
                // Date dots
                g2.fillRect(x + 2, y + 6, 2, 2);
                g2.fillRect(x + 5, y + 6, 2, 2);
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    private String eventKey(ClanEvent event)
    {
        return Objects.toString(event.getName(), "")
            + "|"
            + Objects.toString(event.getDateTime(), "");
    }

    private EventTone eventTone(ClanEvent event, boolean isLive, boolean past)
    {
        boolean happening = isLive || (event != null && event.isHappeningNow());
        if (happening)
        {
            return EventTone.LIVE;
        }
        if (past || (event != null && event.isPast()))
        {
            return EventTone.PAST;
        }
        if (event != null && event.isToday())
        {
            return EventTone.TODAY;
        }
        return EventTone.FUTURE;
    }

    /**
     * Index among same-tone events earlier in the list, so siblings share a family
     * but get a distinct shade.
     */
    private int toneVariantInList(List<ClanEvent> events, int index)
    {
        ClanEvent target = events.get(index);
        EventTone tone = eventTone(target, false, target.isPast());
        int variant = 0;
        for (int i = 0; i < index; i++)
        {
            ClanEvent other = events.get(i);
            if (eventTone(other, false, other.isPast()) == tone)
            {
                variant++;
            }
        }
        return variant;
    }

    private Color toneColor(EventTone tone, int variant)
    {
        Color[] palette;
        switch (tone)
        {
            case LIVE:
                palette = TONE_LIVE;
                break;
            case TODAY:
                palette = TONE_TODAY;
                break;
            case FUTURE:
                palette = TONE_FUTURE;
                break;
            case PAST:
            default:
                palette = TONE_PAST;
                break;
        }
        return palette[Math.floorMod(variant, palette.length)];
    }

    private JPanel createTimelineEventRow(
        ClanEvent event,
        boolean isLive,
        boolean isNext,
        boolean past,
        int toneVariant,
        boolean connectTop,
        boolean connectBottom
    )
    {
        boolean expanded = Objects.equals(expandedEventKey, eventKey(event));
        boolean isHappening = isLive || event.isHappeningNow();
        boolean isImminent = !past && event.isImminent();
        EventTone tone = eventTone(event, isLive, past);
        Color accent = toneColor(tone, toneVariant);

        int width = contentWidth();
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(BACKGROUND_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMinimumSize(new Dimension(0, 1));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        RailSegment rail = new RailSegment(accent, NODE_SIZE, connectTop, connectBottom);
        rail.setPreferredSize(new Dimension(RAIL_WIDTH, 1));
        row.add(rail, BorderLayout.WEST);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BACKGROUND_COLOR);
        body.setBorder(new EmptyBorder(5, 0, 10, 0));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel nameRow = new JPanel(new BorderLayout(4, 0));
        nameRow.setBackground(BACKGROUND_COLOR);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String name = event.getName() == null ? "Untitled" : event.getName();
        String prefix = isHappening ? "LIVE · " : "";
        int maxChars = Math.max(18, (width - RAIL_WIDTH - 18) / 6);
        String displayName = prefix + name;
        if (displayName.length() > maxChars)
        {
            displayName = displayName.substring(0, maxChars) + "…";
        }

        JLabel nameLabel = new JLabel(plainLabelText(displayName));
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(accent);
        nameLabel.setToolTipText(plainLabelText(name));
        nameRow.add(nameLabel, BorderLayout.CENTER);

        JLabel chevron = new JLabel(expanded ? "▾" : "▸");
        chevron.setFont(FontManager.getRunescapeSmallFont());
        chevron.setForeground(MUTED_TEXT);
        nameRow.add(chevron, BorderLayout.EAST);
        body.add(nameRow);

        JLabel metaLabel = new JLabel(plainLabelText(event.getTimelineMetaLine()));
        metaLabel.setFont(FontManager.getRunescapeSmallFont());
        metaLabel.setForeground(tone == EventTone.PAST ? MUTED_TEXT
            : (isHappening || isNext || isImminent) ? SECONDARY_TEXT
            : MUTED_TEXT);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(metaLabel);

        if (!expanded)
        {
            String host = event.getHost();
            if (host != null && !host.trim().isEmpty() && (isHappening || isNext))
            {
                JLabel hostPeek = new JLabel(plainLabelText(host.trim()));
                hostPeek.setFont(FontManager.getRunescapeSmallFont());
                hostPeek.setForeground(MUTED_TEXT);
                hostPeek.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(hostPeek);
            }
        }

        if (expanded)
        {
            body.add(Box.createVerticalStrut(4));
            body.add(createExpandedDetails(event));
        }

        row.add(body, BorderLayout.CENTER);

        List<JComponent> hoverTargets = new ArrayList<>();
        hoverTargets.add(row);
        hoverTargets.add(body);
        hoverTargets.add(nameRow);

        MouseAdapter toggle = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                String key = eventKey(event);
                expandedEventKey = Objects.equals(expandedEventKey, key) ? null : key;
                updateEvents();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                for (JComponent c : hoverTargets)
                {
                    c.setBackground(HOVER_COLOR);
                }
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                for (JComponent c : hoverTargets)
                {
                    c.setBackground(BACKGROUND_COLOR);
                }
            }
        };
        for (JComponent c : hoverTargets)
        {
            c.addMouseListener(toggle);
        }
        nameLabel.addMouseListener(toggle);
        metaLabel.addMouseListener(toggle);
        chevron.addMouseListener(toggle);
        rail.addMouseListener(toggle);

        return row;
    }

    /**
     * Left-column spine segment: vertical rail + optional square status node.
     */
    private static final class RailSegment extends JPanel
    {
        private final Color nodeColor;
        private final int nodeSize;
        private final boolean connectTop;
        private final boolean connectBottom;

        private RailSegment(Color nodeColor, int nodeSize, boolean connectTop, boolean connectBottom)
        {
            this.nodeColor = nodeColor;
            this.nodeSize = nodeSize;
            this.connectTop = connectTop;
            this.connectBottom = connectBottom;
            setOpaque(false);
            setPreferredSize(new Dimension(RAIL_WIDTH, 12));
            setMinimumSize(new Dimension(RAIL_WIDTH, 12));
            setMaximumSize(new Dimension(RAIL_WIDTH, Integer.MAX_VALUE));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                int midX = getWidth() / 2;
                // Node rows: pin the node near the top so it lines up with the event name even
                // when the row grows (expanded details); tall rows use a fixed offset past the
                // body's top padding. Day markers (no node) just join at the vertical middle.
                int midY = nodeSize > 0 ? Math.max(nodeSize, 10) : getHeight() / 2;
                if (getHeight() > 28 && nodeSize > 0)
                {
                    midY = 8 + nodeSize / 2;
                }

                g2.setColor(RAIL_COLOR);
                g2.setStroke(new BasicStroke(2f));
                if (connectTop)
                {
                    g2.drawLine(midX, 0, midX, midY);
                }
                if (connectBottom)
                {
                    g2.drawLine(midX, midY, midX, getHeight());
                }

                if (nodeColor != null && nodeSize > 0)
                {
                    int x = midX - nodeSize / 2;
                    int y = midY - nodeSize / 2;
                    g2.setColor(nodeColor);
                    g2.fillRect(x, y, nodeSize, nodeSize);
                }
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    private JPanel createExpandedDetails(ClanEvent event)
    {
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(BACKGROUND_COLOR);
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.setBorder(new EmptyBorder(2, 0, 0, 0));
        details.setMaximumSize(new Dimension(CONTENT_WIDTH, Short.MAX_VALUE));

        if (event.getHost() != null && !event.getHost().isEmpty())
        {
            details.add(detailLine("Host  " + event.getHost()));
        }
        if (event.getLocation() != null && !event.getLocation().isEmpty())
        {
            details.add(detailLine("At  " + event.getLocation()));
        }
        if (event.getDescription() != null && !event.getDescription().isEmpty())
        {
            String desc = event.getDescription();
            String full = desc;
            if (desc.length() > MAX_DESC_LENGTH)
            {
                desc = desc.substring(0, MAX_DESC_LENGTH) + "…";
            }
            JLabel descLabel = new JLabel("<html><body style='width:" + (TEXT_WIDTH - 20)
                + "px;color:#c8c8c8'>" + escapeHtml(desc) + "</body></html>");
            descLabel.setFont(FontManager.getRunescapeSmallFont());
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (full.length() > MAX_DESC_LENGTH)
            {
                descLabel.setToolTipText("<html><body style='width:280px'>"
                    + escapeHtml(full) + "</body></html>");
            }
            details.add(Box.createVerticalStrut(3));
            details.add(descLabel);
        }
        if (event.hasUrl())
        {
            details.add(Box.createVerticalStrut(4));
            JLabel linkLabel = new JLabel("Open link ›");
            linkLabel.setFont(FontManager.getRunescapeSmallFont());
            linkLabel.setForeground(LINK_COLOR);
            linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            linkLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            linkLabel.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    LinkBrowser.browse(event.getUrl());
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    linkLabel.setText("<html><u>Open link ›</u></html>");
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    linkLabel.setText("Open link ›");
                }
            });
            details.add(linkLabel);
        }
        return details;
    }

    private JLabel detailLine(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(SECONDARY_TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(1, 0, 1, 0));
        return label;
    }

    public void showError(String message)
    {
        SwingUtilities.invokeLater(() ->
        {
            eventsContainer.removeAll();
            JLabel errorLabel = new JLabel("<html><center>" + message + "</center></html>");
            errorLabel.setFont(FontManager.getRunescapeFont());
            errorLabel.setForeground(new Color(255, 100, 100));
            errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            errorLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            eventsContainer.add(errorLabel);
            eventsContainer.add(Box.createVerticalGlue());
            statusLabel.setText("Error");
            refreshHealthDot();
            eventsContainer.revalidate();
            eventsContainer.repaint();
        });
    }

    public void showLoading()
    {
        SwingUtilities.invokeLater(() ->
        {
            eventsContainer.removeAll();
            JLabel loadingLabel = new JLabel("Loading events...");
            loadingLabel.setFont(FontManager.getRunescapeFont());
            loadingLabel.setForeground(SECONDARY_TEXT);
            loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            loadingLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            eventsContainer.add(loadingLabel);
            eventsContainer.add(Box.createVerticalGlue());
            statusLabel.setText("Loading...");
            refreshHealthDot();
            eventsContainer.revalidate();
            eventsContainer.repaint();
        });
    }

    /**
     * API strings starting with {@code <html>} would be rendered as HTML by Swing;
     * a leading space disables that.
     */
    private static String plainLabelText(String text)
    {
        if (text == null)
        {
            return "";
        }
        return BasicHTML.isHTMLString(text) ? " " + text : text;
    }

    private String escapeHtml(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br>");
    }
}
