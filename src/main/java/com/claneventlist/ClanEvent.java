package com.claneventlist;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Represents a clan event fetched from the Google Sheet.
 * 
 * Expected columns: Event Name, Description, Date, Start Time, End Time, Host, Location, URL
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClanEvent implements Comparable<ClanEvent>
{
    private String name;
    private String description;
    private LocalDateTime dateTime;      // Start date/time
    private LocalTime endTime;           // End time (same day)
    private String host;
    private String location;
    private String url;                  // Link to more info / signup

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
    private static final DateTimeFormatter SHORT_FORMAT = DateTimeFormatter.ofPattern("MMM dd HH:mm");
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM dd");

    /**
     * Returns a formatted string of the event date/time.
     */
    public String getFormattedDateTime()
    {
        if (dateTime == null)
        {
            return "TBD";
        }
        return dateTime.format(DISPLAY_FORMAT);
    }

    /**
     * Returns a short formatted string of the event date/time.
     */
    public String getShortDateTime()
    {
        if (dateTime == null)
        {
            return "TBD";
        }
        return dateTime.format(SHORT_FORMAT);
    }

    /**
     * Returns just the day (e.g., "Mon, Dec 15").
     */
    public String getDayString()
    {
        if (dateTime == null)
        {
            return "TBD";
        }
        return dateTime.format(DAY_FORMAT);
    }

    /**
     * Returns the time range (e.g., "20:00 - 22:00" or just "20:00").
     */
    public String getTimeRange()
    {
        if (dateTime == null)
        {
            return "TBD";
        }
        String start = dateTime.format(TIME_ONLY);
        if (endTime != null)
        {
            return start + " - " + endTime.format(TIME_ONLY);
        }
        return start;
    }

    /**
     * Returns the duration string if end time is set.
     */
    public String getDuration()
    {
        if (dateTime == null || endTime == null)
        {
            return null;
        }
        
        LocalTime startTime = dateTime.toLocalTime();
        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        
        // Handle overnight events
        if (minutes < 0)
        {
            minutes += 24 * 60;
        }
        
        long hours = minutes / 60;
        long mins = minutes % 60;
        
        if (hours > 0 && mins > 0)
        {
            return hours + "h " + mins + "m";
        }
        else if (hours > 0)
        {
            return hours + "h";
        }
        else
        {
            return mins + "m";
        }
    }

    /**
     * Returns a human-readable time until the event.
     */
    public String getTimeUntil()
    {
        if (dateTime == null)
        {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        if (dateTime.isBefore(now))
        {
            return "Started";
        }

        long days = ChronoUnit.DAYS.between(now, dateTime);
        long hours = ChronoUnit.HOURS.between(now, dateTime) % 24;
        long minutes = ChronoUnit.MINUTES.between(now, dateTime) % 60;

        if (days > 0)
        {
            return days + "d " + hours + "h";
        }
        else if (hours > 0)
        {
            return hours + "h " + minutes + "m";
        }
        else
        {
            return minutes + "m";
        }
    }

    /**
     * Checks if this event has a URL.
     */
    public boolean hasUrl()
    {
        return url != null && !url.trim().isEmpty();
    }

    /**
     * Checks if this event is in the future.
     */
    public boolean isUpcoming()
    {
        return dateTime != null && dateTime.isAfter(LocalDateTime.now());
    }

    /**
     * Checks if this event is currently happening.
     */
    public boolean isHappeningNow()
    {
        if (dateTime == null)
        {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = endTime != null 
            ? dateTime.toLocalDate().atTime(endTime) 
            : dateTime.plusHours(2); // Assume 2 hour default
            
        return now.isAfter(dateTime) && now.isBefore(end);
    }

    /**
     * Checks if this event is happening today.
     */
    public boolean isToday()
    {
        if (dateTime == null)
        {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return dateTime.toLocalDate().equals(now.toLocalDate());
    }

    /**
     * Checks if this event is happening within the next hour.
     */
    public boolean isImminent()
    {
        if (dateTime == null)
        {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(now, dateTime);
        return minutes >= 0 && minutes <= 60;
    }

    @Override
    public int compareTo(ClanEvent other)
    {
        if (this.dateTime == null && other.dateTime == null)
        {
            return 0;
        }
        if (this.dateTime == null)
        {
            return 1;
        }
        if (other.dateTime == null)
        {
            return -1;
        }
        return this.dateTime.compareTo(other.dateTime);
    }
}

