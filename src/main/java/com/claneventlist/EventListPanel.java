package com.claneventlist;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Side panel that displays a list of all clan events.
 */
@Slf4j
public class EventListPanel extends PluginPanel
{
    private static final Color BACKGROUND_COLOR = ColorScheme.DARK_GRAY_COLOR;
    private static final Color PANEL_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color HOVER_COLOR = ColorScheme.DARK_GRAY_HOVER_COLOR;
    private static final Color ACCENT_COLOR = new Color(255, 200, 0);
    private static final Color IMMINENT_COLOR = new Color(255, 100, 100);
    private static final Color HAPPENING_COLOR = new Color(100, 255, 100);
    private static final Color LINK_COLOR = new Color(100, 180, 255);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color SECONDARY_TEXT = ColorScheme.LIGHT_GRAY_COLOR;
    
    private static final int EVENT_SPACING = 8;
    private static final int MAX_DESC_LENGTH = 400;
    // RuneLite panel is ~225px usable, minus padding = content width
    private static final int CONTENT_WIDTH = 205;
    private static final int TEXT_WIDTH = 140; // Text wrap width (account for emoji + padding + borders)
    
    private static boolean DEBUG_SIZES = false;

    private final EventListConfig config;
    private final GoogleSheetService sheetService;
    private final Runnable refreshCallback;
    private final Runnable seasonalLinkCallback;
    private final Runnable seasonalTestCallback;
    private final Runnable seasonalFlushCallback;
    private final Runnable seasonalManifestRefreshCallback;
    private final Runnable seasonalDebugDropCallback;

    private JPanel eventsContainer;
    private JLabel statusLabel;
    private JLabel lastUpdateLabel;
    private JLabel seasonalStatusLabel;
    private CardLayout contentLayout;
    private JPanel contentPanel;
    private JButton eventsTabButton;
    private JButton seasonalTabButton;
    private JLabel seasonalLinkLabel;
    private JLabel seasonalCountsLabel;
    private JLabel seasonalManifestLabel;
    private JLabel seasonalApiLabel;
    private JLabel seasonalErrorLabel;

    public EventListPanel(
        EventListConfig config,
        GoogleSheetService sheetService,
        Runnable refreshCallback,
        Runnable seasonalLinkCallback,
        Runnable seasonalTestCallback,
        Runnable seasonalFlushCallback,
        Runnable seasonalManifestRefreshCallback,
        Runnable seasonalDebugDropCallback
    )
    {
        super(false); // Don't wrap content - we handle our own scrolling
        
        this.config = config;
        this.sheetService = sheetService;
        this.refreshCallback = refreshCallback;
        this.seasonalLinkCallback = seasonalLinkCallback;
        this.seasonalTestCallback = seasonalTestCallback;
        this.seasonalFlushCallback = seasonalFlushCallback;
        this.seasonalManifestRefreshCallback = seasonalManifestRefreshCallback;
        this.seasonalDebugDropCallback = seasonalDebugDropCallback;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createContentPanel(), BorderLayout.CENTER);
        add(createFooterPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel()
    {
        JPanel headerWrapper = new JPanel();
        headerWrapper.setLayout(new BoxLayout(headerWrapper, BoxLayout.Y_AXIS));
        headerWrapper.setBackground(BACKGROUND_COLOR);
        headerWrapper.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(BACKGROUND_COLOR);
        JLabel titleLabel = new JLabel("Clan Events");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLabel.setForeground(ACCENT_COLOR);
        topRow.add(titleLabel, BorderLayout.WEST);

        JButton refreshButton = new JButton("↻");
        refreshButton.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        refreshButton.setForeground(TEXT_COLOR);
        refreshButton.setBackground(PANEL_COLOR);
        refreshButton.setBorder(new EmptyBorder(5, 10, 5, 10));
        refreshButton.setFocusPainted(false);
        refreshButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshButton.setToolTipText("Refresh events from Google Sheet");
        refreshButton.addActionListener(e -> {
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

        JPanel tabsRow = new JPanel(new GridLayout(1, 2, 6, 0));
        tabsRow.setBackground(BACKGROUND_COLOR);
        tabsRow.setBorder(new EmptyBorder(6, 0, 0, 0));

        eventsTabButton = createTabButton("Events", true, () -> setActiveTab("events"));
        seasonalTabButton = createTabButton("Seasonal", false, () -> setActiveTab("seasonal"));
        tabsRow.add(eventsTabButton);
        tabsRow.add(seasonalTabButton);

        headerWrapper.add(topRow);
        headerWrapper.add(tabsRow);
        return headerWrapper;
    }

    private JPanel createContentPanel()
    {
        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.setBackground(BACKGROUND_COLOR);
        contentPanel.add(createEventsScrollPane(), "events");
        contentPanel.add(createSeasonalPanel(), "seasonal");
        contentLayout.show(contentPanel, "events");
        return contentPanel;
    }

    private JScrollPane createEventsScrollPane()
    {
        eventsContainer = new JPanel();
        eventsContainer.setLayout(new BoxLayout(eventsContainer, BoxLayout.Y_AXIS));
        eventsContainer.setBackground(BACKGROUND_COLOR);
        eventsContainer.setBorder(new EmptyBorder(0, 8, 0, 8));

        JScrollPane scrollPane = new JScrollPane(eventsContainer);
        scrollPane.setBackground(BACKGROUND_COLOR);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        return scrollPane;
    }

    private JPanel createSeasonalPanel()
    {
        JPanel seasonalPanel = new JPanel();
        seasonalPanel.setLayout(new BoxLayout(seasonalPanel, BoxLayout.Y_AXIS));
        seasonalPanel.setBackground(BACKGROUND_COLOR);
        seasonalPanel.setBorder(new EmptyBorder(2, 8, 2, 8));

        seasonalLinkLabel = createSeasonalValueLabel("Not linked");
        seasonalCountsLabel = createSeasonalValueLabel("queued=0 sent=0 failed=0");
        seasonalManifestLabel = createSeasonalValueLabel("Manifest not loaded");
        seasonalApiLabel = createSeasonalValueLabel("Last API: Not sent yet");
        seasonalErrorLabel = createSeasonalValueLabel("Last error: none");

        seasonalPanel.add(createInfoRow("Link", seasonalLinkLabel));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createInfoRow("Queue", seasonalCountsLabel));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createInfoRow("Manifest", seasonalManifestLabel));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createInfoRow("API", seasonalApiLabel));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createInfoRow("Error", seasonalErrorLabel));
        seasonalPanel.add(Box.createVerticalStrut(10));

        seasonalPanel.add(createActionButton("Link/Re-link", seasonalLinkCallback));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createActionButton("Test API", seasonalTestCallback));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createActionButton("Flush Queue", seasonalFlushCallback));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createActionButton("Refresh Manifest", seasonalManifestRefreshCallback));
        seasonalPanel.add(Box.createVerticalStrut(4));
        seasonalPanel.add(createActionButton("Debug: Queue Fake Drop", seasonalDebugDropCallback));

        JLabel trustNote = new JLabel("<html><body style='width:170px;color:#b0b0b0'>"
            + "Identity, team, event, and scoring remain server-authoritative. "
            + "Local filters are optimization only."
            + "</body></html>");
        trustNote.setFont(FontManager.getRunescapeSmallFont());
        trustNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        trustNote.setBorder(new EmptyBorder(10, 0, 0, 0));
        seasonalPanel.add(trustNote);
        seasonalPanel.add(Box.createVerticalGlue());
        return seasonalPanel;
    }

    private JPanel createFooterPanel()
    {
        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setBackground(BACKGROUND_COLOR);
        footerPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(BACKGROUND_COLOR);

        statusLabel = new JLabel("Loading...");
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(SECONDARY_TEXT);
        topRow.add(statusLabel, BorderLayout.WEST);

        lastUpdateLabel = new JLabel("");
        lastUpdateLabel.setFont(FontManager.getRunescapeSmallFont());
        lastUpdateLabel.setForeground(SECONDARY_TEXT);
        topRow.add(lastUpdateLabel, BorderLayout.EAST);

        seasonalStatusLabel = new JLabel("Seasonal: disabled");
        seasonalStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        seasonalStatusLabel.setForeground(SECONDARY_TEXT);

        footerPanel.add(topRow);
        footerPanel.add(Box.createVerticalStrut(3));
        footerPanel.add(seasonalStatusLabel);

        return footerPanel;
    }

    private JButton createTabButton(String label, boolean active, Runnable onClick)
    {
        JButton button = new JButton(label);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(TEXT_COLOR);
        button.setBackground(active ? HOVER_COLOR : PANEL_COLOR);
        button.setBorder(new EmptyBorder(4, 8, 4, 8));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> onClick.run());
        return button;
    }

    private void setActiveTab(String tabKey)
    {
        contentLayout.show(contentPanel, tabKey);
        boolean eventsActive = "events".equals(tabKey);
        eventsTabButton.setBackground(eventsActive ? HOVER_COLOR : PANEL_COLOR);
        seasonalTabButton.setBackground(eventsActive ? PANEL_COLOR : HOVER_COLOR);
    }

    private JPanel createInfoRow(String label, JLabel valueLabel)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(BACKGROUND_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel key = new JLabel(label + ":");
        key.setFont(FontManager.getRunescapeSmallFont());
        key.setForeground(SECONDARY_TEXT);
        row.add(key, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.CENTER);
        return row;
    }

    private JLabel createSeasonalValueLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(TEXT_COLOR);
        return label;
    }

    private JButton createActionButton(String label, Runnable action)
    {
        JButton button = new JButton(label);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(TEXT_COLOR);
        button.setBackground(PANEL_COLOR);
        button.setBorder(new EmptyBorder(5, 8, 5, 8));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e ->
        {
            if (action != null)
            {
                action.run();
            }
        });
        return button;
    }

    /**
     * Updates the panel with the latest events.
     */
    public void updateEvents()
    {
        SwingUtilities.invokeLater(() -> {
            eventsContainer.removeAll();

            List<ClanEvent> events;
            if (config.showPastEvents())
            {
                events = sheetService.getCachedEvents();
            }
            else
            {
                events = sheetService.getUpcomingEvents();
            }

            int maxEvents = config.maxEventsInPanel();
            int displayCount = Math.min(events.size(), maxEvents);

            if (events.isEmpty())
            {
                JLabel noEventsLabel = new JLabel("No upcoming events");
                noEventsLabel.setFont(FontManager.getRunescapeFont());
                noEventsLabel.setForeground(SECONDARY_TEXT);
                noEventsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                noEventsLabel.setBorder(new EmptyBorder(20, 0, 10, 0));
                eventsContainer.add(noEventsLabel);

                if (config.sheetId().isEmpty())
                {
                    JLabel configLabel = new JLabel("<html><center>Configure your Google Sheet ID<br>in the plugin settings</center></html>");
                    configLabel.setFont(FontManager.getRunescapeSmallFont());
                    configLabel.setForeground(SECONDARY_TEXT);
                    configLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    eventsContainer.add(configLabel);
                }
            }
            else
            {
                // Get the highlighted event (active or next upcoming)
                ClanEvent highlightedEvent = sheetService.getHighlightedEvent();
                ClanEvent activeEvent = sheetService.getActiveEvent();
                
                // Show active event section at the top if there's one happening
                if (activeEvent != null)
                {
                    // Active event header
                    JLabel activeHeader = new JLabel("● ACTIVE EVENT");
                    activeHeader.setFont(FontManager.getRunescapeBoldFont());
                    activeHeader.setForeground(HAPPENING_COLOR);
                    activeHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
                    eventsContainer.add(activeHeader);
                    eventsContainer.add(Box.createVerticalStrut(4));
                    
                    eventsContainer.add(createEventPanel(activeEvent, true));
                    eventsContainer.add(Box.createVerticalStrut(EVENT_SPACING));
                    
                    // Separator
                    JSeparator separator = new JSeparator();
                    separator.setForeground(SECONDARY_TEXT);
                    separator.setMaximumSize(new Dimension(CONTENT_WIDTH, 2));
                    separator.setAlignmentX(Component.LEFT_ALIGNMENT);
                    eventsContainer.add(separator);
                    eventsContainer.add(Box.createVerticalStrut(8));
                    
                    // Upcoming events header
                    JLabel upcomingHeader = new JLabel("UPCOMING");
                    upcomingHeader.setFont(FontManager.getRunescapeBoldFont());
                    upcomingHeader.setForeground(ACCENT_COLOR);
                    upcomingHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
                    eventsContainer.add(upcomingHeader);
                    eventsContainer.add(Box.createVerticalStrut(EVENT_SPACING));
                }
                
                for (int i = 0; i < displayCount; i++)
                {
                    ClanEvent event = events.get(i);
                    
                    // Skip active event in the main list since it's shown at top
                    if (activeEvent != null && event.equals(activeEvent))
                    {
                        continue;
                    }
                    
                    // Highlight if this is the next upcoming event (not active, since active is shown separately)
                    boolean isHighlighted = activeEvent == null && highlightedEvent != null && event.equals(highlightedEvent);
                    eventsContainer.add(createEventPanel(event, isHighlighted));
                    
                    // Add spacing between events (not after the last one)
                    if (i < displayCount - 1)
                    {
                        eventsContainer.add(Box.createVerticalStrut(EVENT_SPACING));
                    }
                }

                if (events.size() > maxEvents)
                {
                    eventsContainer.add(Box.createVerticalStrut(EVENT_SPACING));
                    
                    JLabel moreLabel = new JLabel("+" + (events.size() - maxEvents) + " more events");
                    moreLabel.setFont(FontManager.getRunescapeSmallFont());
                    moreLabel.setForeground(SECONDARY_TEXT);
                    moreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    eventsContainer.add(moreLabel);
                }
            }

            // Add vertical glue to push content to top
            eventsContainer.add(Box.createVerticalGlue());

            // Update status
            statusLabel.setText(events.size() + " event" + (events.size() != 1 ? "s" : ""));

            // Update last fetch time
            long lastFetch = sheetService.getLastFetchTime();
            if (lastFetch > 0)
            {
                long minutesAgo = (System.currentTimeMillis() - lastFetch) / 60000;
                if (minutesAgo < 1)
                {
                    lastUpdateLabel.setText("Just now");
                }
                else
                {
                    lastUpdateLabel.setText(minutesAgo + "m ago");
                }
            }
            else
            {
                lastUpdateLabel.setText("Not synced");
            }

            eventsContainer.revalidate();
            eventsContainer.repaint();
            
            // Debug sizes after layout
            if (DEBUG_SIZES)
            {
                SwingUtilities.invokeLater(() -> {
                    log.info("=== DEBUG SIZES ===");
                    log.info("Panel width: {}, Content width constant: {}, Text width constant: {}", 
                        EventListPanel.this.getWidth(), CONTENT_WIDTH, TEXT_WIDTH);
                    log.info("EventsContainer size: {}x{}", eventsContainer.getWidth(), eventsContainer.getHeight());
                    log.info("EventsContainer preferred: {}x{}", 
                        eventsContainer.getPreferredSize().width, eventsContainer.getPreferredSize().height);
                    
                    for (int i = 0; i < eventsContainer.getComponentCount(); i++)
                    {
                        Component c = eventsContainer.getComponent(i);
                        log.info("Component {}: {} - size: {}x{}, preferred: {}x{}, min: {}x{}, max: {}x{}", 
                            i, c.getClass().getSimpleName(),
                            c.getWidth(), c.getHeight(),
                            c.getPreferredSize().width, c.getPreferredSize().height,
                            c.getMinimumSize().width, c.getMinimumSize().height,
                            c.getMaximumSize().width, c.getMaximumSize().height);
                    }
                    log.info("===================");
                });
            }
        });
    }

    private JPanel createEventPanel(ClanEvent event, boolean isNext)
    {
        JPanel eventPanel = new JPanel();
        eventPanel.setLayout(new BoxLayout(eventPanel, BoxLayout.Y_AXIS));
        eventPanel.setBackground(PANEL_COLOR);
        eventPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        eventPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Constrain event panel width - BoxLayout respects this
        eventPanel.setMaximumSize(new Dimension(CONTENT_WIDTH, Short.MAX_VALUE));

        // Determine status and colors
        boolean isHappening = event.isHappeningNow();
        boolean isImminent = event.isImminent();
        
        Color borderColor = null;
        if (isHappening)
        {
            borderColor = HAPPENING_COLOR;
        }
        else if (isImminent)
        {
            borderColor = IMMINENT_COLOR;
        }
        else if (isNext)
        {
            borderColor = ACCENT_COLOR;
        }

        // Apply border
        if (borderColor != null)
        {
            eventPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 2),
                new EmptyBorder(6, 6, 6, 6)
            ));
        }

        // === EVENT NAME (wraps to multiple lines, max ~60 chars) ===
        String name = event.getName();
        String displayName = name;
        // Limit characters but allow word wrap for 2+ lines
        int maxNameChars = 60;
        if (name.length() > maxNameChars)
        {
            displayName = name.substring(0, maxNameChars) + "...";
        }
        
        String nameColor;
        if (isHappening)
        {
            nameColor = "#64ff64";
        }
        else if (isImminent)
        {
            nameColor = "#ff6464";
        }
        else if (isNext)
        {
            nameColor = "#ffc800";
        }
        else
        {
            nameColor = "#ffffff";
        }
        // Use word-wrap to allow multi-line names
        JLabel nameLabel = new JLabel("<html><div style='width: " + TEXT_WIDTH + "px; word-wrap: break-word'>" + 
            "<span style='color: " + nameColor + "'>" + escapeHtml(displayName) + "</span></div></html>");
        nameLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        nameLabel.setToolTipText(name); // Full name in tooltip
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventPanel.add(nameLabel);
        eventPanel.add(Box.createVerticalStrut(8));

        // HTML style for emoji-compatible line height
        String emojiStyle = "style='width: " + TEXT_WIDTH + "px; line-height: 1.4; padding-top: 2px'";

        // === DATE ===
        JLabel dateLabel = new JLabel("<html><body " + emojiStyle + ">" +
            "📅 <span style='color: #b0b0b0'>Date:</span> " + 
            "<span style='color: " + (event.isToday() ? "#64c864" : "#ffffff") + "'>" + 
            escapeHtml(event.getDayString()) + "</span></body></html>");
        dateLabel.setFont(FontManager.getRunescapeSmallFont());
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventPanel.add(dateLabel);
        eventPanel.add(Box.createVerticalStrut(4));

        // === TIME ===
        String timeText = event.getTimeRange();
        String duration = event.getDuration();
        if (duration != null)
        {
            timeText += " (" + duration + ")";
        }
        JLabel timeLabel = new JLabel("<html><body " + emojiStyle + ">" +
            "🕐 <span style='color: #b0b0b0'>Time:</span> " + 
            "<span style='color: #ffffff'>" + escapeHtml(timeText) + "</span></body></html>");
        timeLabel.setFont(FontManager.getRunescapeSmallFont());
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventPanel.add(timeLabel);
        eventPanel.add(Box.createVerticalStrut(4));

        // === HOST ===
        boolean hasHost = event.getHost() != null && !event.getHost().isEmpty();
        if (hasHost)
        {
            JLabel hostLabel = new JLabel("<html><body " + emojiStyle + ">" +
                "📣 <span style='color: #b0b0b0'>Host:</span> " + 
                "<span style='color: #ffffff'>" + escapeHtml(event.getHost()) + "</span></body></html>");
            hostLabel.setFont(FontManager.getRunescapeSmallFont());
            hostLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(hostLabel);
            eventPanel.add(Box.createVerticalStrut(4));
        }

        // === LOCATION ===
        boolean hasLocation = event.getLocation() != null && !event.getLocation().isEmpty();
        if (hasLocation)
        {
            JLabel locationLabel = new JLabel("<html><body " + emojiStyle + ">" +
                "📍 <span style='color: #b0b0b0'>Location:</span> " + 
                "<span style='color: #ffffff'>" + escapeHtml(event.getLocation()) + "</span></body></html>");
            locationLabel.setFont(FontManager.getRunescapeSmallFont());
            locationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(locationLabel);
            eventPanel.add(Box.createVerticalStrut(4));
        }

        // === DESCRIPTION ===
        if (event.getDescription() != null && !event.getDescription().isEmpty())
        {
            eventPanel.add(Box.createVerticalStrut(4));
            
            String desc = event.getDescription();
            String fullDesc = desc;
            if (desc.length() > MAX_DESC_LENGTH)
            {
                desc = desc.substring(0, MAX_DESC_LENGTH) + "...";
            }
            
            JLabel descHeaderLabel = new JLabel("<html><body " + emojiStyle + ">" +
                "📝 <span style='color: #b0b0b0'>Description:</span></body></html>");
            descHeaderLabel.setFont(FontManager.getRunescapeSmallFont());
            descHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(descHeaderLabel);
            eventPanel.add(Box.createVerticalStrut(3));
            
            JLabel descLabel = new JLabel("<html><body style='width: " + TEXT_WIDTH + "px; color: #c8c8c8'>" + 
                escapeHtml(desc) + "</body></html>");
            descLabel.setFont(FontManager.getRunescapeSmallFont());
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (fullDesc.length() > MAX_DESC_LENGTH)
            {
                descLabel.setToolTipText("<html><body style='width: 300px'>" + 
                    escapeHtml(fullDesc) + "</body></html>");
            }
            eventPanel.add(descLabel);
        }

        // === LINK ===
        if (event.hasUrl())
        {
            eventPanel.add(Box.createVerticalStrut(6));
            
            JLabel linkLabel = new JLabel("🔗 More Info");
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
                    linkLabel.setText("<html><u>🔗 More Info</u></html>");
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    linkLabel.setText("🔗 More Info");
                }
            });
            
            eventPanel.add(linkLabel);
        }

        // === STATUS / COUNTDOWN (at bottom) ===
        eventPanel.add(Box.createVerticalStrut(8));
        if (isHappening)
        {
            JLabel statusLbl = new JLabel("<html><body " + emojiStyle + ">" +
                "🟢 <span style='color: #64ff64'><b>HAPPENING NOW</b></span></body></html>");
            statusLbl.setFont(FontManager.getRunescapeBoldFont());
            statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(statusLbl);
        }
        else if (event.isUpcoming())
        {
            String countdownColor = isImminent ? "#ff6464" : "#ffffff";
            JLabel countdownLabel = new JLabel("<html><body " + emojiStyle + ">" +
                "⏱️ <span style='color: " + countdownColor + "'><b>Starts in: " + 
                escapeHtml(event.getTimeUntil()) + "</b></span></body></html>");
            countdownLabel.setFont(FontManager.getRunescapeBoldFont());
            countdownLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(countdownLabel);
        }

        // Hover effect for the whole panel
        final Color originalBg = PANEL_COLOR;
        eventPanel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                eventPanel.setBackground(HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                eventPanel.setBackground(originalBg);
            }
        });

        return eventPanel;
    }

    /**
     * Shows an error message in the panel.
     */
    public void showError(String message)
    {
        SwingUtilities.invokeLater(() -> {
            eventsContainer.removeAll();

            JLabel errorLabel = new JLabel("<html><center>" + message + "</center></html>");
            errorLabel.setFont(FontManager.getRunescapeFont());
            errorLabel.setForeground(IMMINENT_COLOR);
            errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            errorLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            eventsContainer.add(errorLabel);
            eventsContainer.add(Box.createVerticalGlue());

            statusLabel.setText("Error");
            
            eventsContainer.revalidate();
            eventsContainer.repaint();
        });
    }

    public void updateSeasonalTelemetry(SeasonalTelemetry telemetry)
    {
        if (telemetry == null)
        {
            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            String status = "Seasonal: "
                + telemetry.getLinkStatus()
                + " | queued=" + telemetry.getQueuedCount()
                + " sent=" + telemetry.getSentCount()
                + " failed=" + telemetry.getFailedCount();
            if (telemetry.isPausedForAuth())
            {
                status += " | auth paused";
            }
            seasonalStatusLabel.setText(status);
            seasonalStatusLabel.setToolTipText("<html>Last API: " + escapeHtml(telemetry.getLastApiResponse())
                + "<br>Last error: " + escapeHtml(telemetry.getLastError()) + "</html>");

            if (seasonalLinkLabel != null)
            {
                seasonalLinkLabel.setText(telemetry.getLinkStatus());
            }
            if (seasonalCountsLabel != null)
            {
                String counts = "queued=" + telemetry.getQueuedCount()
                    + " sent=" + telemetry.getSentCount()
                    + " failed=" + telemetry.getFailedCount()
                    + (telemetry.isPausedForAuth() ? " (auth paused)" : "");
                seasonalCountsLabel.setText(counts);
            }
            if (seasonalManifestLabel != null)
            {
                seasonalManifestLabel.setText(telemetry.getManifestStatus());
            }
            if (seasonalApiLabel != null)
            {
                seasonalApiLabel.setText(truncateText("Last API: " + telemetry.getLastApiResponse(), 72));
                seasonalApiLabel.setToolTipText(telemetry.getLastApiResponse());
            }
            if (seasonalErrorLabel != null)
            {
                String err = telemetry.getLastError() == null || telemetry.getLastError().isEmpty()
                    ? "none"
                    : telemetry.getLastError();
                seasonalErrorLabel.setText(truncateText("Last error: " + err, 72));
                seasonalErrorLabel.setToolTipText(err);
            }
        });
    }

    /**
     * Shows a loading state in the panel.
     */
    public void showLoading()
    {
        SwingUtilities.invokeLater(() -> {
            eventsContainer.removeAll();

            JLabel loadingLabel = new JLabel("Loading events...");
            loadingLabel.setFont(FontManager.getRunescapeFont());
            loadingLabel.setForeground(SECONDARY_TEXT);
            loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            loadingLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            eventsContainer.add(loadingLabel);
            eventsContainer.add(Box.createVerticalGlue());

            statusLabel.setText("Loading...");

            eventsContainer.revalidate();
            eventsContainer.repaint();
        });
    }

    /**
     * Escapes HTML special characters in a string.
     */
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

    private String truncateText(String text, int maxLength)
    {
        if (text == null || text.length() <= maxLength)
        {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
