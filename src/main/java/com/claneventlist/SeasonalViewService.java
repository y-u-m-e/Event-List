package com.claneventlist;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Opt-in fetch of {@code GET /plugins/seasonal/view}. Call {@link #refresh} off the client thread. */
@Slf4j
@Singleton
public class SeasonalViewService
{
    private final EventListConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private volatile SeasonalView view;
    private volatile long lastFetchTime;

    @Inject
    SeasonalViewService(EventListConfig config, OkHttpClient httpClient, Gson gson)
    {
        this.config = config;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public SeasonalView getView()
    {
        return view;
    }

    public boolean needsRefresh(int minutes)
    {
        return System.currentTimeMillis() - lastFetchTime >= Math.max(1, minutes) * 60_000L;
    }

    public void clear()
    {
        view = null;
        lastFetchTime = 0;
    }

    /** Blocking. Keeps the previous view on any failure. */
    public void refresh(String rsn)
    {
        lastFetchTime = System.currentTimeMillis();
        try
        {
            HttpUrl base = HttpUrl.parse(EventsApiService.resolveApiBase(config) + "/plugins/seasonal/view");
            if (base == null)
            {
                return;
            }
            Request req = new Request.Builder()
                .url(base.newBuilder().addQueryParameter("rsn", rsn == null ? "" : rsn).build())
                .header("User-Agent", "RuneLite-IronforgedClanEvents")
                .header("Accept", "application/json")
                .get()
                .build();
            try (Response response = httpClient.newCall(req).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    log.debug("Seasonal view failed: HTTP {}", response.code());
                    return;
                }
                SeasonalView parsed = parse(gson, response.body().string());
                if (parsed != null)
                {
                    view = parsed;
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Seasonal view error: {}", e.toString());
        }
    }

    /** Null on empty or malformed input; never throws. */
    static SeasonalView parse(Gson gson, String json)
    {
        try
        {
            return gson.newBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
                .fromJson(json, SeasonalView.class);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
