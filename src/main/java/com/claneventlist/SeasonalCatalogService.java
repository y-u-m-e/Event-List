package com.claneventlist;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fetches GET /plugins/seasonal for the event identity: the passphrase shown on the overlay and the
 * event id that team roster sync asks for. The drop catalog in the same payload is ignored now that
 * plugin drop submit is removed (restore from git tag seasonal-submit-v1).
 */
@Slf4j
@Singleton
public class SeasonalCatalogService
{
    private final EventListConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private volatile String eventId = "";
    private volatile String eventPassphrase = "";
    private volatile long lastFetchTime = 0;

    @Inject
    public SeasonalCatalogService(EventListConfig config, OkHttpClient httpClient, Gson gson)
    {
        this.config = config;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public synchronized void refresh()
    {
        Request request = new Request.Builder()
            .url(EventsApiService.resolveApiBase(config) + "/plugins/seasonal")
            .header("User-Agent", "RuneLite-IronforgedClanEvents")
            .header("Accept", "application/json")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                // Keep the previous values; lastFetchTime is not advanced, so the next tick retries.
                log.warn("Seasonal event fetch failed: HTTP {}", response.code());
                return;
            }

            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            JsonObject event = root != null && root.has("event") && root.get("event").isJsonObject()
                ? root.getAsJsonObject("event")
                : null;

            String nextEventId = "";
            String nextEventPassphrase = "";
            if (event != null)
            {
                nextEventId = firstNonEmpty(
                    text(event, "seasonal_event_id"),
                    firstNonEmpty(text(event, "id"), text(event, "plugin_keyword"))
                );
                nextEventPassphrase = firstNonEmpty(
                    text(event, "event_passphrase"),
                    firstNonEmpty(
                        text(event, "seasonal_event_passphrase"),
                        firstNonEmpty(text(event, "passphrase"), text(event, "event_key"))
                    )
                );
            }

            eventId = nextEventId;
            eventPassphrase = nextEventPassphrase;
            lastFetchTime = System.currentTimeMillis();
            log.debug("Seasonal event refreshed: id={}", nextEventId);
        }
        catch (Exception e)
        {
            log.warn("Seasonal event error: {}", e.getMessage());
        }
    }

    public void clear()
    {
        eventId = "";
        eventPassphrase = "";
        lastFetchTime = 0;
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

    /**
     * Seasonal Event ID. Prefers the config value when set; otherwise the API value.
     */
    public String resolveEventId(EventListConfig config)
    {
        if (config != null && config.seasonalEventId() != null)
        {
            String fromConfig = config.seasonalEventId().trim();
            if (!fromConfig.isEmpty())
            {
                return fromConfig;
            }
        }
        return eventId == null ? "" : eventId;
    }

    /**
     * Seasonal Event Passphrase for the overlay. Prefers the config value when set; otherwise the API value.
     */
    public String resolveEventPassphrase(EventListConfig config)
    {
        if (config != null && config.seasonalEventPassphrase() != null)
        {
            String fromConfig = config.seasonalEventPassphrase().trim();
            if (!fromConfig.isEmpty())
            {
                return fromConfig;
            }
        }
        return eventPassphrase == null ? "" : eventPassphrase;
    }

    private static String text(JsonObject row, String key)
    {
        if (row == null || !row.has(key) || !row.get(key).isJsonPrimitive())
        {
            return "";
        }
        return row.get(key).getAsString().trim();
    }

    private static String firstNonEmpty(String a, String b)
    {
        if (a != null && !a.isEmpty())
        {
            return a;
        }
        return b == null ? "" : b;
    }
}
