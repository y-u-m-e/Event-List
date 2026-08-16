package com.claneventlist;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Represents a clan event from the Iron Forged events API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClanEvent implements Comparable<ClanEvent>
{
    private String name;
    private String description;
    private LocalDateTime dateTime;      // Start date/time (UTC wall)
    private LocalTime endTime;           // End clock time (UTC), optional
    private String host;
    private String location;
    private String url;                  // Link to more info / signup
    /** Full UTC end instant when provided by the API (preferred over endTime alone). */
    private LocalDateTime endDateTime;
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DAY_BLOCK_FORMAT = DateTimeFormatter.ofPattern("EEE MMM d");
    
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId LOCAL = ZoneId.systemDefault();
    private static final DateTimeFormatter ZONE_ABBR =
        DateTimeFormatter.ofPattern("z").withZone(LOCAL);
    
    /**
     * Converts the stored UTC dateTime to local timezone.
     */
    private LocalDateTime getLocalDateTime()
    {
        java.time.ZonedDateTime zoned = getStartZoned();
        return zoned == null ? null : zoned.toLocalDateTime();
    }

    /**
     * Local calendar date for day-block grouping.
     */
    public LocalDate getLocalDate()
    {
        LocalDateTime local = getLocalDateTime();
        return local == null ? null : local.toLocalDate();
    }

    /**
     * Compact day-block label (e.g. "SAT AUG 15") in local timezone.
     */
    public String getDayBlockLabel()
    {
        LocalDateTime local = getLocalDateTime();
        if (local == null)
        {
            return "TBD";
        }
        return local.format(DAY_BLOCK_FORMAT).toUpperCase();
    }

    /**
     * True if the event name tags Skill of the Week (SOTW).
     */
    public boolean isSkillOfTheWeek()
    {
        return nameContainsToken("SOTW");
    }

    /**
     * True if the event name tags Boss of the Week (BOTW).
     */
    public boolean isBossOfTheWeek()
    {
        return nameContainsToken("BOTW");
    }

    /**
     * True for weekly spotlight events (SOTW or BOTW).
     */
    public boolean isWeeklySpotlight()
    {
        return isSkillOfTheWeek() || isBossOfTheWeek();
    }

    private boolean nameContainsToken(String token)
    {
        if (name == null || name.isEmpty())
        {
            return false;
        }
        String upper = name.toUpperCase();
        int idx = upper.indexOf(token);
        while (idx >= 0)
        {
            boolean startOk = idx == 0 || !Character.isLetterOrDigit(upper.charAt(idx - 1));
            int end = idx + token.length();
            boolean endOk = end >= upper.length() || !Character.isLetterOrDigit(upper.charAt(end));
            if (startOk && endOk)
            {
                return true;
            }
            idx = upper.indexOf(token, idx + 1);
        }
        return false;
    }

    /**
     * Day-block header always includes an explicit date (e.g. "TODAY · SAT AUG 15").
     */
    public String getDayBlockHeader()
    {
        String label = getDayBlockLabel();
        if (isToday())
        {
            return "TODAY · " + label;
        }
        if (isTomorrow())
        {
            return "TOMORROW · " + label;
        }
        return label;
    }

    /**
     * Timeline meta line: "18:00 – 19:00 · in 2h 33m" / "15:00 · passed".
     * Weekly SOTW/BOTW span a full week from start.
     */
    public String getTimelineMetaLine()
    {
        if (isWeeklySpotlight())
        {
            if (isUpcoming())
            {
                return "all week · in " + getTimeUntil();
            }
            if (isHappeningNow())
            {
                return "all week · " + formatDurationUntil(getEffectiveEndDateTime()) + " left";
            }
            return "all week · ended";
        }

        String time = getTimeRange().replace(" - ", " – ");
        if (isHappeningNow())
        {
            return time + " · now";
        }
        if (isUpcoming())
        {
            return time + " · in " + getTimeUntil();
        }
        return time + " · passed";
    }

    /**
     * Effective end instant in UTC. Weekly spotlights last 7 days from start.
     */
    public LocalDateTime getEffectiveEndDateTime()
    {
        if (dateTime == null)
        {
            return null;
        }
        if (isWeeklySpotlight())
        {
            return dateTime.plusDays(7);
        }
        if (endDateTime != null)
        {
            return endDateTime;
        }
        if (endTime != null)
        {
            LocalDateTime end = dateTime.toLocalDate().atTime(endTime);
            if (!end.isAfter(dateTime))
            {
                end = end.plusDays(1);
            }
            return end;
        }
        return dateTime.plusHours(2);
    }

    /**
     * True when the event window has fully ended.
     */
    public boolean isPast()
    {
        LocalDateTime end = getEffectiveEndDateTime();
        return end != null && !nowUtc().isBefore(end);
    }
    
    /**
     * Converts the stored UTC endTime to local timezone.
     */
    private LocalTime getLocalEndTime()
    {
        java.time.ZonedDateTime end = getEndZoned();
        return end == null ? null : end.toLocalTime();
    }

    private java.time.ZonedDateTime getStartZoned()
    {
        if (dateTime == null)
        {
            return null;
        }
        return dateTime.atZone(UTC).withZoneSameInstant(LOCAL);
    }

    private java.time.ZonedDateTime getEndZoned()
    {
        if (endDateTime != null)
        {
            return endDateTime.atZone(UTC).withZoneSameInstant(LOCAL);
        }
        if (endTime == null || dateTime == null)
        {
            return null;
        }
        java.time.ZonedDateTime start = getStartZoned();
        java.time.ZonedDateTime end = dateTime.toLocalDate().atTime(endTime)
            .atZone(UTC)
            .withZoneSameInstant(LOCAL);
        if (start != null && !end.isAfter(start))
        {
            end = end.plusDays(1);
        }
        return end;
    }

    /**
     * Returns the time range in the user's local timezone (e.g. "1:00 PM – 2:00 PM PDT").
     * Sheet times are stored as UTC and converted here.
     */
    public String getTimeRange()
    {
        java.time.ZonedDateTime start = getStartZoned();
        if (start == null)
        {
            return "TBD";
        }
        String startStr = start.format(TIME_ONLY);
        java.time.ZonedDateTime end = getEndZoned();
        String zone = start.format(ZONE_ABBR);
        if (end != null)
        {
            return startStr + " – " + end.format(TIME_ONLY) + " " + zone;
        }
        return startStr + " " + zone;
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

        return formatDurationUntil(dateTime);
    }

    private String formatDurationUntil(LocalDateTime target)
    {
        if (target == null)
        {
            return "Unknown";
        }

        LocalDateTime now = nowUtc();
        if (!target.isAfter(now))
        {
            return "0m";
        }

        long days = ChronoUnit.DAYS.between(now, target);
        long hours = ChronoUnit.HOURS.between(now, target) % 24;
        long minutes = ChronoUnit.MINUTES.between(now, target) % 60;

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
     * Checks if this event is happening tomorrow (in local timezone).
     */
    public boolean isTomorrow()
    {
        LocalDateTime local = getLocalDateTime();
        if (local == null)
        {
            return false;
        }
        return local.toLocalDate().equals(LocalDateTime.now().toLocalDate().plusDays(1));
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
     * Weekly SOTW/BOTW run for 7 days from their start time.
     */
    public boolean isHappeningNow()
    {
        if (dateTime == null)
        {
            return false;
        }
        LocalDateTime now = nowUtc();
        LocalDateTime end = getEffectiveEndDateTime();
        return !now.isBefore(dateTime) && now.isBefore(end);
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

