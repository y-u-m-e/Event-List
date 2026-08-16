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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches and caches the public seasonal drop catalog.
 * The plugin uses this as the only notable-drop gate (common loot never POSTs).
 */
@Slf4j
@Singleton
public class SeasonalCatalogService
{
    public static final String SEASONAL_API_URL = EventsApiService.API_BASE + "/plugins/seasonal";

    private final EventListConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private volatile List<SeasonalCatalogEntry> catalog = Collections.emptyList();
    private volatile String eventId = "";
    private volatile String eventPassphrase = "";
    private volatile String eventName = "";
    private volatile boolean eventEnded = false;
    private volatile boolean acceptingSubmissions = false;
    private volatile boolean passphraseRequired = false;
    private volatile boolean hasActiveEvent = false;
    private volatile long lastFetchTime = 0;
    private volatile String lastError = "";

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
                lastError = "HTTP " + response.code();
                log.warn("Seasonal catalog fetch failed: {}", lastError);
                // Fail closed: do not keep a stale allowlist forever after hard errors mid-event;
                // keep previous catalog so brief blips do not wipe eligibility.
                return;
            }

            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null)
            {
                lastError = "empty payload";
                return;
            }

            List<SeasonalCatalogEntry> next = new ArrayList<>();
            JsonArray rows = root.has("catalog") && root.get("catalog").isJsonArray()
                ? root.getAsJsonArray("catalog")
                : null;
            if (rows != null)
            {
                for (JsonElement element : rows)
                {
                    if (!element.isJsonObject())
                    {
                        continue;
                    }
                    JsonObject row = element.getAsJsonObject();
                    String boss = text(row, "boss");
                    String item = text(row, "item");
                    if (boss.isEmpty() || item.isEmpty())
                    {
                        continue;
                    }
                    int itemId = intVal(row, "item_id");
                    if (itemId <= 0)
                    {
                        itemId = intVal(row, "itemId");
                    }
                    next.add(new SeasonalCatalogEntry(boss, item, itemId));
                }
            }

            JsonObject event = root.has("event") && root.get("event").isJsonObject()
                ? root.getAsJsonObject("event")
                : null;
            if (event == null || event.isJsonNull())
            {
                hasActiveEvent = false;
                eventId = "";
                eventPassphrase = "";
                eventName = "";
                eventEnded = false;
            }
            else
            {
                hasActiveEvent = true;
                eventId = firstNonEmpty(
                    text(event, "seasonal_event_id"),
                    firstNonEmpty(text(event, "id"), text(event, "plugin_keyword"))
                );
                // Seasonal Event Passphrase — overlay text + submit auth (not the event id).
                eventPassphrase = firstNonEmpty(
                    text(event, "event_passphrase"),
                    firstNonEmpty(
                        text(event, "seasonal_event_passphrase"),
                        firstNonEmpty(text(event, "passphrase"), text(event, "event_key"))
                    )
                );
                eventName = text(event, "name");
                eventEnded = event.has("event_ended") && event.get("event_ended").getAsBoolean();
            }

            acceptingSubmissions = root.has("accepting_submissions")
                && root.get("accepting_submissions").getAsBoolean();
            // Older responses: treat a non-empty catalog as accepting.
            if (!root.has("accepting_submissions"))
            {
                acceptingSubmissions = !next.isEmpty();
            }

            passphraseRequired = root.has("passphrase_required")
                && root.get("passphrase_required").getAsBoolean();
            catalog = Collections.unmodifiableList(next);
            lastFetchTime = System.currentTimeMillis();
            lastError = "";
            log.debug(
                "Seasonal catalog refreshed: active={} ended={} id={} passphrase={} entries={} accepting={}",
                hasActiveEvent,
                eventEnded,
                eventId,
                eventPassphrase,
                catalog.size(),
                acceptingSubmissions
            );
        }
        catch (Exception e)
        {
            lastError = e.getMessage() == null ? "fetch error" : e.getMessage();
            log.warn("Seasonal catalog error: {}", lastError);
        }
    }

    public void clear()
    {
        catalog = Collections.emptyList();
        eventId = "";
        eventPassphrase = "";
        eventName = "";
        eventEnded = false;
        acceptingSubmissions = false;
        passphraseRequired = false;
        hasActiveEvent = false;
        lastFetchTime = 0;
        lastError = "";
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
     * Returns the catalog row for a match, or null if not notable.
     * Prefers item id when the catalog publishes one.
     */
    public SeasonalCatalogEntry findMatch(String bossName, String itemName, int itemId)
    {
        if (!canSubmitAgainstCatalog())
        {
            return null;
        }
        for (SeasonalCatalogEntry entry : catalog)
        {
            if (entry.matches(bossName, itemName, itemId))
            {
                return entry;
            }
        }
        return null;
    }

    public SeasonalCatalogEntry findMatch(String bossName, String itemName)
    {
        return findMatch(bossName, itemName, 0);
    }

    public boolean canSubmitAgainstCatalog()
    {
        return acceptingSubmissions && !catalog.isEmpty();
    }

    public List<SeasonalCatalogEntry> getCatalog()
    {
        return catalog;
    }

    public boolean isPassphraseRequired()
    {
        return passphraseRequired;
    }

    public boolean hasActiveEvent()
    {
        return hasActiveEvent;
    }

    public boolean isEventEnded()
    {
        return eventEnded;
    }

    public boolean isAcceptingSubmissions()
    {
        return acceptingSubmissions;
    }

    public String getEventName()
    {
        return eventName;
    }

    /**
     * Cached Seasonal Event ID from the last catalog fetch (may be empty).
     */
    public String getEventId()
    {
        return eventId;
    }

    /**
     * Seasonal Event ID for submits.
     * Prefers the config value when set; otherwise the API value.
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
     * Server-published Seasonal Event Passphrase (shown on the overlay).
     */
    public String getEventPassphrase()
    {
        return eventPassphrase;
    }

    /**
     * Seasonal Event Passphrase for overlay + submit auth.
     * Prefers the config value when set; otherwise the API value.
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

    public String getLastError()
    {
        return lastError;
    }

    public int catalogSize()
    {
        return catalog.size();
    }

    private static String text(JsonObject row, String key)
    {
        if (row == null || !row.has(key) || row.get(key).isJsonNull())
        {
            return "";
        }
        return row.get(key).getAsString().trim();
    }

    private static int intVal(JsonObject row, String key)
    {
        if (row == null || !row.has(key) || row.get(key).isJsonNull())
        {
            return 0;
        }
        try
        {
            if (row.get(key).isJsonPrimitive() && row.get(key).getAsJsonPrimitive().isNumber())
            {
                return row.get(key).getAsInt();
            }
            String raw = row.get(key).getAsString().trim();
            if (raw.isEmpty())
            {
                return 0;
            }
            return Integer.parseInt(raw);
        }
        catch (Exception e)
        {
            return 0;
        }
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
