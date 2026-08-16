package com.claneventlist;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches clan events from the public Iron Forged plugins API
 * ({@code GET https://api.ironforged.gg/plugins/events}).
 */
@Slf4j
@Singleton
public class EventsApiService
{
    public static final String API_BASE = "https://api.ironforged.gg";
    public static final String DEV_API_BASE = "https://dev-api.ironforged.gg";

    /**
     * Resolves the active plugin API origin from config.
     */
    public static String resolveApiBase(EventListConfig config)
    {
        return config != null && config.useDevApi() ? DEV_API_BASE : API_BASE;
    }

    public String apiBase()
    {
        return resolveApiBase(config);
    }

    private final EventListConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private List<ClanEvent> cachedEvents = new ArrayList<>();
    private long lastFetchTime = 0;
    private volatile ApiStatus apiStatus = ApiStatus.unknown("Enable Load clan events to check API health");

    @Inject
    public EventsApiService(EventListConfig config, OkHttpClient httpClient, Gson gson)
    {
        this.config = config;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public ApiStatus getApiStatus()
    {
        return apiStatus;
    }

    /**
     * Fetches events from the Iron Forged events API and refreshes health status.
     */
    public List<ClanEvent> fetchEvents()
    {
        HealthPayload health = fetchHealth();
        String eventsUrl = apiBase() + "/plugins/events";
        log.debug("Fetching events from: {}", eventsUrl);

        Request request = new Request.Builder()
            .url(eventsUrl)
            .header("User-Agent", "RuneLite-IronforgedClanEvents")
            .header("Accept", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Failed to fetch events API: HTTP {}", response.code());
                apiStatus = buildStatus(health, false, response.code(), null, cachedEvents.size());
                return cachedEvents;
            }

            okhttp3.ResponseBody responseBody = response.body();
            if (responseBody == null)
            {
                log.warn("Empty response body from events API");
                apiStatus = buildStatus(health, false, response.code(), null, cachedEvents.size());
                return cachedEvents;
            }

            String body = responseBody.string();
            FeedMeta meta = parseFeedMeta(body);
            List<ClanEvent> events = parseEventsJson(body);
            cachedEvents = events;
            lastFetchTime = System.currentTimeMillis();
            apiStatus = buildStatus(health, true, response.code(), meta, events.size());
            log.debug("Fetched {} events", events.size());
            return events;
        }
        catch (Exception e)
        {
            log.warn("Error fetching events API: {}", e.getMessage());
            apiStatus = buildStatus(health, false, 0, null, cachedEvents.size());
            return cachedEvents;
        }
    }

    private HealthPayload fetchHealth()
    {
        Request request = new Request.Builder()
            .url(apiBase() + "/health")
            .header("User-Agent", "RuneLite-IronforgedClanEvents")
            .header("Accept", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                return HealthPayload.failed(response.code());
            }
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null)
            {
                return HealthPayload.failed(response.code());
            }
            return new HealthPayload(
                true,
                response.code(),
                text(root, "status"),
                text(root, "service"),
                text(root, "version"),
                text(root, "timestamp")
            );
        }
        catch (Exception e)
        {
            log.debug("Health check failed: {}", e.getMessage());
            return HealthPayload.failed(0);
        }
    }

    private FeedMeta parseFeedMeta(String body)
    {
        try
        {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (root == null)
            {
                return null;
            }
            boolean ok = !root.has("ok") || root.get("ok").getAsBoolean();
            return new FeedMeta(
                ok,
                text(root, "source"),
                text(root, "updatedAt"),
                text(root, "cache"),
                root.has("count") && root.get("count").isJsonPrimitive()
                    ? root.get("count").getAsInt()
                    : -1
            );
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private ApiStatus buildStatus(
        HealthPayload health,
        boolean eventsOk,
        int eventsHttp,
        FeedMeta meta,
        int eventCount
    )
    {
        String healthBit;
        if (health != null && health.reachable)
        {
            healthBit = health.status.isEmpty() ? "ok" : health.status;
            if (!health.version.isEmpty())
            {
                healthBit += " · v" + health.version;
            }
        }
        else if (health != null && health.httpCode > 0)
        {
            healthBit = "down · HTTP " + health.httpCode;
        }
        else
        {
            healthBit = "unreachable";
        }

        String eventsBit;
        if (eventsOk)
        {
            eventsBit = eventCount + " event" + (eventCount != 1 ? "s" : "");
            if (meta != null && !meta.cache.isEmpty())
            {
                eventsBit += " · " + meta.cache;
            }
        }
        else if (eventsHttp > 0)
        {
            eventsBit = "feed error · HTTP " + eventsHttp;
            if (eventCount > 0)
            {
                eventsBit += " · cached " + eventCount;
            }
        }
        else
        {
            eventsBit = eventCount > 0 ? "feed error · cached " + eventCount : "feed error";
        }

        String tip = "<html>" + escape(healthBit) + "<br>" + escape(eventsBit) + "</html>";

        ApiStatus.Level level;
        if (health != null && health.reachable && eventsOk
            && (health.status.isEmpty() || "healthy".equalsIgnoreCase(health.status)))
        {
            level = ApiStatus.Level.HEALTHY;
        }
        else if ((health != null && health.reachable) || eventsOk || eventCount > 0)
        {
            level = ApiStatus.Level.DEGRADED;
        }
        else
        {
            level = ApiStatus.Level.DOWN;
        }
        return new ApiStatus(level, tip);
    }

    private static String escape(String value)
    {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static final class HealthPayload
    {
        final boolean reachable;
        final int httpCode;
        final String status;
        final String service;
        final String version;
        final String timestamp;

        HealthPayload(boolean reachable, int httpCode, String status, String service, String version, String timestamp)
        {
            this.reachable = reachable;
            this.httpCode = httpCode;
            this.status = status == null ? "" : status;
            this.service = service == null ? "" : service;
            this.version = version == null ? "" : version;
            this.timestamp = timestamp == null ? "" : timestamp;
        }

        static HealthPayload failed(int httpCode)
        {
            return new HealthPayload(false, httpCode, "", "", "", "");
        }
    }

    private static final class FeedMeta
    {
        final boolean ok;
        final String source;
        final String updatedAt;
        final String cache;
        final int count;

        FeedMeta(boolean ok, String source, String updatedAt, String cache, int count)
        {
            this.ok = ok;
            this.source = source == null ? "" : source;
            this.updatedAt = updatedAt == null ? "" : updatedAt;
            this.cache = cache == null ? "" : cache;
            this.count = count;
        }
    }

    List<ClanEvent> parseEventsJson(String body)
    {
        List<ClanEvent> events = new ArrayList<>();
        if (body == null || body.trim().isEmpty())
        {
            return events;
        }

        JsonObject root = gson.fromJson(body, JsonObject.class);
        if (root == null)
        {
            return events;
        }

        JsonArray list = root.has("events") && root.get("events").isJsonArray()
            ? root.getAsJsonArray("events")
            : null;
        if (list == null)
        {
            log.warn("Events API payload missing events array");
            return events;
        }

        for (JsonElement element : list)
        {
            if (!element.isJsonObject())
            {
                continue;
            }
            ClanEvent event = parseEventObject(element.getAsJsonObject());
            if (event != null)
            {
                events.add(event);
            }
        }

        Collections.sort(events);
        return events;
    }

    private ClanEvent parseEventObject(JsonObject row)
    {
        String name = text(row, "name");
        if (name.isEmpty())
        {
            name = text(row, "title");
        }
        if (name.isEmpty())
        {
            return null;
        }

        LocalDateTime start = parseIsoUtc(text(row, "startAt"));
        if (start == null)
        {
            start = parseIsoUtc(text(row, "startTime"));
        }
        if (start == null)
        {
            log.debug("Skipping event without start time: {}", name);
            return null;
        }

        LocalDateTime end = parseIsoUtc(text(row, "endAt"));
        if (end == null)
        {
            end = parseIsoUtc(text(row, "endTime"));
        }

        LocalTime endTime = end != null ? end.toLocalTime() : null;
        String host = text(row, "host");
        if (host.isEmpty())
        {
            host = text(row, "postedBy");
        }

        ClanEvent event = new ClanEvent(
            name,
            text(row, "description"),
            start,
            endTime,
            host,
            text(row, "location"),
            firstNonEmpty(text(row, "url"), text(row, "link")),
            end
        );
        return event;
    }

    private static String text(JsonObject row, String key)
    {
        if (row == null || !row.has(key) || row.get(key).isJsonNull())
        {
            return "";
        }
        return row.get(key).getAsString().trim();
    }

    private static String firstNonEmpty(String a, String b)
    {
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private static LocalDateTime parseIsoUtc(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return null;
        }
        try
        {
            Instant instant = Instant.parse(raw);
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        catch (DateTimeParseException ignored)
        {
            // fall through
        }
        try
        {
            return LocalDateTime.parse(raw);
        }
        catch (DateTimeParseException e)
        {
            log.debug("Could not parse timestamp: {}", raw);
            return null;
        }
    }

    public List<ClanEvent> getCachedEvents()
    {
        return new ArrayList<>(cachedEvents);
    }

    public void clearEvents()
    {
        cachedEvents.clear();
        lastFetchTime = 0;
        apiStatus = ApiStatus.unknown("Enable Load clan events to check API health");
        log.debug("Cleared all cached events");
    }

    public ClanEvent getNextEvent()
    {
        return cachedEvents.stream()
            .filter(ClanEvent::isUpcoming)
            .sorted()
            .findFirst()
            .orElse(null);
    }

    public ClanEvent getActiveEvent()
    {
        return cachedEvents.stream()
            .filter(ClanEvent::isHappeningNow)
            .sorted()
            .findFirst()
            .orElse(null);
    }

    public ClanEvent getHighlightedEvent()
    {
        ClanEvent active = getActiveEvent();
        return active != null ? active : getNextEvent();
    }

    public List<ClanEvent> getUpcomingEvents()
    {
        List<ClanEvent> upcoming = new ArrayList<>();
        for (ClanEvent event : cachedEvents)
        {
            if (event.isUpcoming() || event.isHappeningNow() || (event.isWeeklySpotlight() && !event.isPast()))
            {
                upcoming.add(event);
            }
        }
        Collections.sort(upcoming);
        return upcoming;
    }

    public boolean needsRefresh(int refreshIntervalMinutes)
    {
        if (lastFetchTime == 0)
        {
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastFetchTime;
        return elapsed >= (long) refreshIntervalMinutes * 60_000L;
    }

    public long getLastFetchTime()
    {
        return lastFetchTime;
    }
}
