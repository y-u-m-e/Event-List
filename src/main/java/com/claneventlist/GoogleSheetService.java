package com.claneventlist;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for fetching clan event data from a Google Sheet.
 * 
 * The Google Sheet should have the following columns:
 * A: Event Name
 * B: Description
 * C: Date (format: yyyy-MM-dd or MM/dd/yyyy)
 * D: Start Time (format: HH:mm or h:mm a)
 * E: End Time (format: HH:mm or h:mm a)
 * F: Host
 * G: Location
 * H: URL
 * 
 * The sheet must be published to the web (File > Share > Publish to web)
 * and use the CSV format for public access.
 */
@Slf4j
@Singleton
public class GoogleSheetService
{
    private static final String SHEETS_CSV_URL = "https://docs.google.com/spreadsheets/d/%s/gviz/tq?tqx=out:csv";
    private static final String SHEETS_CSV_URL_WITH_SHEET = "https://docs.google.com/spreadsheets/d/%s/gviz/tq?tqx=out:csv&sheet=%s";

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM-dd-yy"),      // 12-15-24 format
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),    // 12-15-2024 format
        DateTimeFormatter.ofPattern("M-d-yy"),        // 1-5-24 format
        DateTimeFormatter.ofPattern("M-d-yyyy"),      // 1-5-2024 format
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yy"),      // 12/15/24 format
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    private static final DateTimeFormatter[] TIME_FORMATS = {
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("h:mm a"),
        DateTimeFormatter.ofPattern("hh:mm a"),
        DateTimeFormatter.ofPattern("h:mm:ss a"),      // Google Sheets often includes seconds
        DateTimeFormatter.ofPattern("hh:mm:ss a"),     // 12-hour with seconds
        DateTimeFormatter.ofPattern("HH:mm:ss"),
        DateTimeFormatter.ofPattern("H:mm:ss"),
        DateTimeFormatter.ofPattern("h:mma"),          // No space before AM/PM
        DateTimeFormatter.ofPattern("hh:mma"),
        DateTimeFormatter.ofPattern("h:mm:ssa"),       // No space, with seconds
        DateTimeFormatter.ofPattern("hh:mm:ssa")
    };

    private final OkHttpClient httpClient;
    private List<ClanEvent> cachedEvents = new ArrayList<>();
    private long lastFetchTime = 0;

    @Inject
    public GoogleSheetService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    /**
     * Fetches events from the configured Google Sheet.
     */
    public List<ClanEvent> fetchEvents(String sheetId, String sheetName)
    {
        if (sheetId == null || sheetId.trim().isEmpty())
        {
            log.debug("No sheet ID configured");
            return Collections.emptyList();
        }

        String url;
        if (sheetName != null && !sheetName.trim().isEmpty())
        {
            url = String.format(SHEETS_CSV_URL_WITH_SHEET, sheetId.trim(), sheetName.trim());
        }
        else
        {
            url = String.format(SHEETS_CSV_URL, sheetId.trim());
        }

        log.debug("Fetching events from: {}", url);

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "RuneLite")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Failed to fetch sheet data: HTTP {}", response.code());
                return cachedEvents;
            }

            okhttp3.ResponseBody responseBody = response.body();
            if (responseBody == null)
            {
                log.warn("Empty response body from sheet");
                return cachedEvents;
            }
            String body = responseBody.string();
            List<ClanEvent> events = parseCsv(body);
            cachedEvents = events;
            lastFetchTime = System.currentTimeMillis();
            log.debug("Fetched {} events", events.size());
            return events;
        }
        catch (Exception e)
        {
            log.warn("Error fetching sheet data: {}", e.getMessage());
            return cachedEvents;
        }
    }

    /**
     * Returns the cached events without fetching.
     */
    public List<ClanEvent> getCachedEvents()
    {
        return new ArrayList<>(cachedEvents);
    }

    /**
     * Clears all cached events.
     */
    public void clearEvents()
    {
        cachedEvents.clear();
        lastFetchTime = 0;
        log.debug("Cleared all cached events");
    }

    /**
     * Returns the next upcoming event.
     */
    public ClanEvent getNextEvent()
    {
        return cachedEvents.stream()
            .filter(ClanEvent::isUpcoming)
            .sorted()
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns the currently active (happening now) event, if any.
     */
    public ClanEvent getActiveEvent()
    {
        return cachedEvents.stream()
            .filter(ClanEvent::isHappeningNow)
            .sorted()
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns the event that should be highlighted (active or next upcoming).
     */
    public ClanEvent getHighlightedEvent()
    {
        ClanEvent active = getActiveEvent();
        return active != null ? active : getNextEvent();
    }

    /**
     * Returns all upcoming events sorted by date.
     */
    public List<ClanEvent> getUpcomingEvents()
    {
        List<ClanEvent> upcoming = new ArrayList<>();
        for (ClanEvent event : cachedEvents)
        {
            if (event.isUpcoming())
            {
                upcoming.add(event);
            }
        }
        Collections.sort(upcoming);
        return upcoming;
    }

    /**
     * Parses CSV data into a list of ClanEvent objects.
     * Handles multiline fields (newlines inside quoted strings).
     */
    private List<ClanEvent> parseCsv(String csv)
    {
        List<ClanEvent> events = new ArrayList<>();
        
        log.debug("Parsing CSV data, length: {}", csv.length());
        
        // Parse all rows from CSV (handles multiline fields)
        List<String[]> rows = parseCsvRows(csv);
        
        log.debug("Parsed {} rows from CSV", rows.size());
        
        boolean firstRow = true;
        int rowNum = 0;
        
        for (String[] fields : rows)
        {
            rowNum++;
            
            // Skip header row
            if (firstRow)
            {
                log.debug("Header row: {} fields", fields.length);
                firstRow = false;
                continue;
            }
            
            // Skip rows with no event name
            if (fields.length < 1 || fields[0].trim().isEmpty())
            {
                log.debug("Row {}: skipping empty row", rowNum);
                continue;
            }
            
            // Validate we have enough fields (at least name and date)
            if (fields.length < 3)
            {
                log.debug("Row {}: skipping row with only {} fields", rowNum, fields.length);
                continue;
            }

            ClanEvent event = new ClanEvent();
            event.setName(getField(fields, 0));
            event.setDescription(getField(fields, 1));
            
            // Parse date and start time
            String dateStr = getField(fields, 2);
            String startTimeStr = getField(fields, 3);
            
            // Validate date field looks like a date (not description overflow)
            if (!looksLikeDate(dateStr))
            {
                log.debug("Row {}: skipping, date field '{}' doesn't look like a date", rowNum, 
                    dateStr.length() > 30 ? dateStr.substring(0, 30) + "..." : dateStr);
                continue;
            }
            
            log.debug("Row {}: Parsing date='{}' startTime='{}'", rowNum, dateStr, startTimeStr);
            event.setDateTime(parseDateTime(dateStr, startTimeStr));
            
            // Skip if date couldn't be parsed
            if (event.getDateTime() == null)
            {
                log.debug("Row {}: skipping, couldn't parse date", rowNum);
                continue;
            }
            
            // Parse end time (column 4)
            String endTimeStr = getField(fields, 4);
            if (!endTimeStr.isEmpty())
            {
                event.setEndTime(parseTime(endTimeStr));
            }
            
            event.setHost(getField(fields, 5));
            event.setLocation(getField(fields, 6));
            
            // Parse URL (column 7)
            String urlStr = getField(fields, 7);
            if (!urlStr.isEmpty())
            {
                event.setUrl(urlStr);
            }

            log.debug("Row {}: Created event '{}' on {}", rowNum, event.getName(), event.getDateTime());
            events.add(event);
        }

        return events;
    }

    /**
     * Checks if a string looks like a date (short, contains numbers/dashes/slashes).
     */
    private boolean looksLikeDate(String str)
    {
        if (str == null || str.isEmpty())
        {
            return false;
        }
        // Dates should be short (less than 15 chars) and contain digits
        if (str.length() > 15)
        {
            return false;
        }
        // Should contain at least one digit
        return str.matches(".*\\d.*");
    }

    /**
     * Parses entire CSV string into rows, handling multiline quoted fields.
     */
    private List<String[]> parseCsvRows(String csv)
    {
        List<String[]> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < csv.length(); i++)
        {
            char c = csv.charAt(i);
            
            if (c == '"')
            {
                // Check for escaped quote ""
                if (inQuotes && i + 1 < csv.length() && csv.charAt(i + 1) == '"')
                {
                    currentField.append('"');
                    i++; // Skip the next quote
                }
                else
                {
                    inQuotes = !inQuotes;
                }
            }
            else if (c == ',' && !inQuotes)
            {
                // End of field
                currentRow.add(currentField.toString().trim());
                currentField = new StringBuilder();
            }
            else if ((c == '\n' || c == '\r') && !inQuotes)
            {
                // End of row (but skip \r\n as single newline)
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n')
                {
                    i++; // Skip the \n
                }
                
                // Add last field and complete the row
                currentRow.add(currentField.toString().trim());
                if (!currentRow.isEmpty() && !isRowEmpty(currentRow))
                {
                    rows.add(currentRow.toArray(new String[0]));
                }
                currentRow = new ArrayList<>();
                currentField = new StringBuilder();
            }
            else
            {
                // Regular character (including newlines inside quotes)
                currentField.append(c);
            }
        }
        
        // Don't forget the last row
        currentRow.add(currentField.toString().trim());
        if (!currentRow.isEmpty() && !isRowEmpty(currentRow))
        {
            rows.add(currentRow.toArray(new String[0]));
        }
        
        return rows;
    }

    /**
     * Checks if a row is effectively empty.
     */
    private boolean isRowEmpty(List<String> row)
    {
        for (String field : row)
        {
            if (field != null && !field.trim().isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Safely gets a field from the array, returning empty string if out of bounds.
     */
    private String getField(String[] fields, int index)
    {
        if (index < fields.length)
        {
            String value = fields[index].trim();
            // Remove surrounding quotes if present
            if (value.startsWith("\"") && value.endsWith("\""))
            {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return "";
    }

    /**
     * Parses date and time strings into a LocalDateTime.
     * For 2-digit years, assumes the next upcoming occurrence of that date.
     */
    private LocalDateTime parseDateTime(String dateStr, String timeStr)
    {
        if (dateStr == null || dateStr.isEmpty())
        {
            return null;
        }

        java.time.LocalDate date = null;
        java.time.LocalTime time = java.time.LocalTime.NOON; // Default to noon if no time specified
        boolean usedTwoDigitYear = false;

        // Try to parse the date
        for (DateTimeFormatter format : DATE_FORMATS)
        {
            try
            {
                date = java.time.LocalDate.parse(dateStr, format);
                // Check if this format uses 2-digit year (ends with 2 digits, not 4)
                if (dateStr.matches(".*\\d{2}$") && !dateStr.matches(".*\\d{4}$"))
                {
                    usedTwoDigitYear = true;
                }
                break;
            }
            catch (DateTimeParseException ignored)
            {
            }
        }

        if (date == null)
        {
            log.debug("Could not parse date: {}", dateStr);
            return null;
        }

        // For 2-digit years: if the date is more than 6 months in the past,
        // assume it should be next year (handles year rollover for events)
        if (usedTwoDigitYear)
        {
            java.time.LocalDate today = java.time.LocalDate.now();
            // If the parsed date is more than 6 months ago, add a year
            if (date.isBefore(today.minusMonths(6)))
            {
                date = date.plusYears(1);
                log.debug("Adjusted 2-digit year date to: {}", date);
            }
        }

        // Try to parse the time if provided
        if (timeStr != null && !timeStr.isEmpty())
        {
            String normalizedTime = timeStr.trim().toUpperCase();
            boolean timeParsed = false;
            for (DateTimeFormatter format : TIME_FORMATS)
            {
                try
                {
                    time = java.time.LocalTime.parse(normalizedTime, format);
                    timeParsed = true;
                    break;
                }
                catch (DateTimeParseException ignored)
                {
                }
            }
            if (!timeParsed)
            {
                log.debug("Could not parse time '{}' (normalized: '{}'), using noon default", timeStr, normalizedTime);
            }
        }

        return LocalDateTime.of(date, time);
    }

    /**
     * Parses a time string into a LocalTime.
     */
    private java.time.LocalTime parseTime(String timeStr)
    {
        if (timeStr == null || timeStr.isEmpty())
        {
            return null;
        }
        
        for (DateTimeFormatter format : TIME_FORMATS)
        {
            try
            {
                return java.time.LocalTime.parse(timeStr.toUpperCase(), format);
            }
            catch (DateTimeParseException ignored)
            {
            }
        }
        
        log.debug("Could not parse time: {}", timeStr);
        return null;
    }

    /**
     * Returns the timestamp of the last successful fetch.
     */
    public long getLastFetchTime()
    {
        return lastFetchTime;
    }

    /**
     * Checks if the cache needs refreshing based on the interval.
     */
    public boolean needsRefresh(int intervalMinutes)
    {
        long intervalMs = intervalMinutes * 60 * 1000L;
        return System.currentTimeMillis() - lastFetchTime > intervalMs;
    }
}

