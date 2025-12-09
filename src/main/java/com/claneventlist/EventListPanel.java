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

    private JPanel eventsContainer;
    private JLabel statusLabel;
    private JLabel lastUpdateLabel;

    public EventListPanel(EventListConfig config, GoogleSheetService sheetService, Runnable refreshCallback)
    {
        super(false); // Don't wrap content - we handle our own scrolling
        
        this.config = config;
        this.sheetService = sheetService;
        this.refreshCallback = refreshCallback;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createContentPanel(), BorderLayout.CENTER);
        add(createFooterPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel()
    {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BACKGROUND_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Title
        JLabel titleLabel = new JLabel("Clan Events");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLabel.setForeground(ACCENT_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        // Refresh button
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
        headerPanel.add(refreshButton, BorderLayout.EAST);

        return headerPanel;
    }

    private JScrollPane createContentPanel()
    {
        // Use BoxLayout for the events container - it respects max sizes
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

    private JPanel createFooterPanel()
    {
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(BACKGROUND_COLOR);
        footerPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

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
}
