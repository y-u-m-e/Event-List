package com.claneventlist;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
    
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId LOCAL = ZoneId.systemDefault();
    
    /**
     * Converts the stored UTC dateTime to local timezone.
     */
    private LocalDateTime getLocalDateTime()
    {
        if (dateTime == null)
        {
            return null;
        }
        return dateTime.atZone(UTC).withZoneSameInstant(LOCAL).toLocalDateTime();
    }
    
    /**
     * Converts the stored UTC endTime to local timezone.
     */
    private LocalTime getLocalEndTime()
    {
        if (endTime == null || dateTime == null)
        {
            return null;
        }
        // Create a full datetime for the end time using same date as start
        LocalDateTime endDateTime = dateTime.toLocalDate().atTime(endTime);
        return endDateTime.atZone(UTC).withZoneSameInstant(LOCAL).toLocalTime();
    }

    /**
     * Returns a formatted string of the event date/time in local timezone.
     */
    public String getFormattedDateTime()
    {
        LocalDateTime local = getLocalDateTime();
        if (local == null)
        {
            return "TBD";
        }
        return local.format(DISPLAY_FORMAT);
    }

    /**
     * Returns a short formatted string of the event date/time in local timezone.
     */
    public String getShortDateTime()
    {
        LocalDateTime local = getLocalDateTime();
        if (local == null)
        {
            return "TBD";
        }
        return local.format(SHORT_FORMAT);
    }

    /**
     * Returns just the day (e.g., "Mon, Dec 15") in local timezone.
     */
    public String getDayString()
    {
        LocalDateTime local = getLocalDateTime();
        if (local == null)
        {
            return "TBD";
        }
        return local.format(DAY_FORMAT);
    }

    /**
     * Returns the time range (e.g., "20:00 - 22:00" or just "20:00") in local timezone.
     */
    public String getTimeRange()
    {
        LocalDateTime local = getLocalDateTime();
        if (local == null)
        {
            return "TBD";
        }
        String start = local.format(TIME_ONLY);
        LocalTime localEnd = getLocalEndTime();
        if (localEnd != null)
        {
            return start + " - " + localEnd.format(TIME_ONLY);
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
     * Gets the current time in UTC for comparisons with stored dateTime.
     */
    private LocalDateTime nowUtc()
    {
        return LocalDateTime.now(UTC);
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

        LocalDateTime now = nowUtc();
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
        return dateTime != null && dateTime.isAfter(nowUtc());
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
        LocalDateTime now = nowUtc();
        LocalDateTime end = endTime != null 
            ? dateTime.toLocalDate().atTime(endTime) 
            : dateTime.plusHours(2); // Assume 2 hour default
            
        return now.isAfter(dateTime) && now.isBefore(end);
    }

    /**
     * Checks if this event is happening today (in local timezone).
     */
    public boolean isToday()
    {
        LocalDateTime local = getLocalDateTime();
        if (local == null)
        {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return local.toLocalDate().equals(now.toLocalDate());
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
        LocalDateTime now = nowUtc();
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

