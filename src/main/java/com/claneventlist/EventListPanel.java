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
    private static final Color TODAY_COLOR = new Color(100, 200, 100);
    private static final Color LINK_COLOR = new Color(100, 180, 255);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color SECONDARY_TEXT = ColorScheme.LIGHT_GRAY_COLOR;
    
    private static final int EVENT_SPACING = 8;
    private static final int MAX_NAME_LENGTH = 40;
    private static final int MAX_DESC_LENGTH = 400;
    private static final int TEXT_WIDTH = 135; // Width for HTML text wrapping
    private static final int EVENT_PANEL_WIDTH = 165; // Max width for event cards

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
        eventsContainer = new JPanel();
        eventsContainer.setLayout(new GridBagLayout());
        eventsContainer.setBackground(BACKGROUND_COLOR);
        eventsContainer.setBorder(new EmptyBorder(0, 4, 0, 12)); // Padding inside scroll area (extra right for scrollbar)

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

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 0, EVENT_SPACING, 0);

            if (events.isEmpty())
            {
                gbc.anchor = GridBagConstraints.CENTER;
                gbc.fill = GridBagConstraints.NONE;
                
                JLabel noEventsLabel = new JLabel("No upcoming events");
                noEventsLabel.setFont(FontManager.getRunescapeFont());
                noEventsLabel.setForeground(SECONDARY_TEXT);
                noEventsLabel.setBorder(new EmptyBorder(20, 0, 10, 0));
                eventsContainer.add(noEventsLabel, gbc);
                gbc.gridy++;

                if (config.sheetId().isEmpty())
                {
                    JLabel configLabel = new JLabel("<html><center>Configure your Google Sheet ID<br>in the plugin settings</center></html>");
                    configLabel.setFont(FontManager.getRunescapeSmallFont());
                    configLabel.setForeground(SECONDARY_TEXT);
                    eventsContainer.add(configLabel, gbc);
                }
            }
            else
            {
                for (int i = 0; i < displayCount; i++)
                {
                    ClanEvent event = events.get(i);
                    
                    // No spacing after last item
                    if (i == displayCount - 1)
                    {
                        gbc.insets = new Insets(0, 0, 0, 0);
                    }
                    
                    eventsContainer.add(createEventPanel(event, i == 0), gbc);
                    gbc.gridy++;
                }

                if (events.size() > maxEvents)
                {
                    gbc.insets = new Insets(EVENT_SPACING, 0, 0, 0);
                    gbc.anchor = GridBagConstraints.CENTER;
                    gbc.fill = GridBagConstraints.NONE;
                    
                    JLabel moreLabel = new JLabel("+" + (events.size() - maxEvents) + " more events");
                    moreLabel.setFont(FontManager.getRunescapeSmallFont());
                    moreLabel.setForeground(SECONDARY_TEXT);
                    eventsContainer.add(moreLabel, gbc);
                    gbc.gridy++;
                }
            }

            // Add filler to push content to top
            gbc.weighty = 1;
            gbc.fill = GridBagConstraints.BOTH;
            eventsContainer.add(Box.createGlue(), gbc);

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
        });
    }

    private JPanel createEventPanel(ClanEvent event, boolean isNext)
    {
        JPanel eventPanel = new JPanel();
        eventPanel.setLayout(new BoxLayout(eventPanel, BoxLayout.Y_AXIS));
        eventPanel.setBackground(PANEL_COLOR);
        eventPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        eventPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Constrain event panel width
        eventPanel.setMaximumSize(new Dimension(EVENT_PANEL_WIDTH, Integer.MAX_VALUE));

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

        // Row 1: Event name (truncated with tooltip)
        String name = event.getName();
        String displayName = name;
        if (name.length() > MAX_NAME_LENGTH)
        {
            displayName = name.substring(0, MAX_NAME_LENGTH) + "...";
        }
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(12f));
        nameLabel.setToolTipText(name);
        if (isHappening)
        {
            nameLabel.setForeground(HAPPENING_COLOR);
        }
        else if (isImminent)
        {
            nameLabel.setForeground(IMMINENT_COLOR);
        }
        else if (isNext)
        {
            nameLabel.setForeground(ACCENT_COLOR);
        }
        else
        {
            nameLabel.setForeground(TEXT_COLOR);
        }
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventPanel.add(nameLabel);
        eventPanel.add(Box.createVerticalStrut(2));

        // Row 2: Date and time info
        String dateTimeText = event.getDayString() + " • " + event.getTimeRange();
        String duration = event.getDuration();
        if (duration != null)
        {
            dateTimeText += " (" + duration + ")";
        }
        
        JLabel dateTimeLabel = new JLabel(dateTimeText);
        dateTimeLabel.setFont(FontManager.getRunescapeSmallFont());
        dateTimeLabel.setForeground(event.isToday() ? TODAY_COLOR : SECONDARY_TEXT);
        dateTimeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventPanel.add(dateTimeLabel);
        eventPanel.add(Box.createVerticalStrut(2));

        // Row 3: Countdown / Status
        if (isHappening)
        {
            JLabel statusLbl = new JLabel("● HAPPENING NOW");
            statusLbl.setFont(FontManager.getRunescapeBoldFont());
            statusLbl.setForeground(HAPPENING_COLOR);
            statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(statusLbl);
        }
        else if (event.isUpcoming())
        {
            JLabel countdownLabel = new JLabel("Starts in: " + event.getTimeUntil());
            countdownLabel.setFont(FontManager.getRunescapeBoldFont());
            countdownLabel.setForeground(isImminent ? IMMINENT_COLOR : TEXT_COLOR);
            countdownLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(countdownLabel);
        }

        // Row 4: Host and Location (if available)
        boolean hasHost = event.getHost() != null && !event.getHost().isEmpty();
        boolean hasLocation = event.getLocation() != null && !event.getLocation().isEmpty();
        
        if (hasHost || hasLocation)
        {
            eventPanel.add(Box.createVerticalStrut(4));
            
            StringBuilder details = new StringBuilder();
            if (hasHost)
            {
                details.append("Host: ").append(event.getHost());
            }
            if (hasHost && hasLocation)
            {
                details.append(" • ");
            }
            if (hasLocation)
            {
                details.append("@ ").append(event.getLocation());
            }
            
            JLabel detailsLabel = new JLabel("<html><body style='width: " + TEXT_WIDTH + "px'>" + 
                escapeHtml(details.toString()) + "</body></html>");
            detailsLabel.setFont(FontManager.getRunescapeSmallFont());
            detailsLabel.setForeground(SECONDARY_TEXT);
            detailsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            eventPanel.add(detailsLabel);
        }

        // Row 5: Description (if available, with text wrapping)
        if (event.getDescription() != null && !event.getDescription().isEmpty())
        {
            eventPanel.add(Box.createVerticalStrut(4));
            
            String desc = event.getDescription();
            String fullDesc = desc;
            if (desc.length() > MAX_DESC_LENGTH)
            {
                desc = desc.substring(0, MAX_DESC_LENGTH) + "...";
            }
            // Use HTML for text wrapping with constrained width
            String htmlDesc = "<html><body style='width: " + TEXT_WIDTH + "px'>" + 
                escapeHtml(desc) + "</body></html>";
            JLabel descLabel = new JLabel(htmlDesc);
            descLabel.setFont(FontManager.getRunescapeSmallFont());
            descLabel.setForeground(SECONDARY_TEXT);
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (fullDesc.length() > MAX_DESC_LENGTH)
            {
                descLabel.setToolTipText("<html><body style='width: 300px'>" + 
                    escapeHtml(fullDesc) + "</body></html>");
            }
            eventPanel.add(descLabel);
        }

        // Row 6: Link button (if URL available)
        if (event.hasUrl())
        {
            eventPanel.add(Box.createVerticalStrut(6));
            
            JLabel linkLabel = new JLabel("[Link] More Info");
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
                    linkLabel.setText("<html><u>[Link] More Info</u></html>");
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    linkLabel.setText("[Link] More Info");
                }
            });
            
            eventPanel.add(linkLabel);
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

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.CENTER;

            JLabel errorLabel = new JLabel("<html><center>" + message + "</center></html>");
            errorLabel.setFont(FontManager.getRunescapeFont());
            errorLabel.setForeground(IMMINENT_COLOR);
            errorLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            eventsContainer.add(errorLabel, gbc);

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

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.CENTER;

            JLabel loadingLabel = new JLabel("Loading events...");
            loadingLabel.setFont(FontManager.getRunescapeFont());
            loadingLabel.setForeground(SECONDARY_TEXT);
            loadingLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            eventsContainer.add(loadingLabel, gbc);

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

