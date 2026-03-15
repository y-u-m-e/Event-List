package com.claneventlist;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Slf4j
@Singleton
public class SeasonalApiClient
{
    private static final String DEFAULT_API_BASE_URL = "https://api.emuy.gg";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final EventListConfig config;
    private final Gson gson = new Gson();

    @Inject
    public SeasonalApiClient(OkHttpClient httpClient, EventListConfig config)
    {
        this.httpClient = httpClient;
        this.config = config;
    }

    public SeasonalApiResult submitBulk(SeasonalIdentity identity, List<SeasonalSubmission> submissions)
    {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("submissions", submissions);
        String body = gson.toJson(payload);

        Request request = new Request.Builder()
            .url(apiBaseUrl() + "/clan/seasonal-event/submissions/bulk")
            .post(RequestBody.create(JSON, body))
            .header("User-Agent", "RuneLite Clan Event List")
            .build();
        SeasonalApiResult result = executeSubmissionRequest(request);

        if (result.getHttpCode() == 404 || result.getHttpCode() == 405)
        {
            return new SeasonalApiResult(false, false, false, false, false, result.getHttpCode(), "bulk_unsupported");
        }

        return result;
    }

    public SeasonalApiResult submitSingle(SeasonalIdentity identity, SeasonalSubmission submission)
    {
        String body = gson.toJson(submission);
        Request request = new Request.Builder()
            .url(apiBaseUrl() + "/clan/seasonal-event/submissions")
            .post(RequestBody.create(JSON, body))
            .header("User-Agent", "RuneLite Clan Event List")
            .build();
        return executeSubmissionRequest(request);
    }

    public SeasonalApiResult checkState(SeasonalIdentity identity)
    {
        Request request = new Request.Builder()
            .url(apiBaseUrl() + "/clan/seasonal-event/state")
            .get()
            .header("User-Agent", "RuneLite Clan Event List")
            .build();
        return executeSubmissionRequest(request);
    }

    public JsonObject fetchStateJson(SeasonalIdentity identity)
    {
        Request request = new Request.Builder()
            .url(apiBaseUrl() + "/clan/seasonal-event/state")
            .get()
            .header("User-Agent", "RuneLite Clan Event List")
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            okhttp3.ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null)
            {
                return null;
            }
            return new JsonParser().parse(body.string()).getAsJsonObject();
        }
        catch (Exception ex)
        {
            log.warn("State fetch error: {}", ex.getMessage());
            return null;
        }
    }

    public SeasonalEligibilityManifest parseEligibilityManifest(JsonObject stateJson)
    {
        SeasonalEligibilityManifest manifest = new SeasonalEligibilityManifest();
        if (stateJson == null)
        {
            return manifest;
        }

        JsonObject source = stateJson;
        if (stateJson.has("eligibility") && stateJson.get("eligibility").isJsonObject())
        {
            source = stateJson.getAsJsonObject("eligibility");
        }

        addBosses(source, manifest, "eligible_boss_keys");
        addBosses(source, manifest, "eligibleBossKeys");
        addBosses(source, manifest, "bosses");

        addItemIds(source, manifest, "eligible_item_ids");
        addItemIds(source, manifest, "eligibleItemIds");
        addItemIds(source, manifest, "item_ids");

        manifest.setLoadedAtEpochMs(System.currentTimeMillis());
        return manifest;
    }

    private SeasonalApiResult executeSubmissionRequest(Request request)
    {
        try (Response response = httpClient.newCall(request).execute())
        {
            okhttp3.ResponseBody body = response.body();
            String responseBody = body != null ? body.string() : "";
            int code = response.code();
            String message = responseBody.length() > 250 ? responseBody.substring(0, 250) : responseBody;

            if (code >= 200 && code < 300)
            {
                String mirrorFailure = detectMirrorOrPayloadFailure(responseBody);
                if (mirrorFailure != null && !mirrorFailure.isEmpty())
                {
                    return new SeasonalApiResult(false, false, false, false, false, code, "validation_error:" + mirrorFailure);
                }
                return new SeasonalApiResult(true, false, false, false, false, code, "ok");
            }
            if (code == 401 || code == 403)
            {
                return new SeasonalApiResult(false, false, false, true, false, code, "auth_failure:" + message);
            }
            if (code == 400)
            {
                String normalized = message.toLowerCase();
                if (normalized.contains("duplicate_window"))
                {
                    return new SeasonalApiResult(false, false, true, false, false, code, "duplicate_window");
                }
                if (normalized.contains("identity mismatch") || normalized.contains("team") || normalized.contains("discord"))
                {
                    return new SeasonalApiResult(false, false, false, false, true, code, "identity_mismatch:" + message);
                }
                return new SeasonalApiResult(false, false, false, false, false, code, "validation_error:" + message);
            }
            if (code >= 500)
            {
                return new SeasonalApiResult(false, true, false, false, false, code, "server_error");
            }
            return new SeasonalApiResult(false, false, false, false, false, code, message);
        }
        catch (Exception ex)
        {
            return new SeasonalApiResult(false, true, false, false, false, 0, ex.getMessage());
        }
    }

    private String detectMirrorOrPayloadFailure(String responseBody)
    {
        if (responseBody == null || responseBody.trim().isEmpty())
        {
            return null;
        }
        try
        {
            JsonElement parsed = new JsonParser().parse(responseBody);
            if (!parsed.isJsonObject())
            {
                return null;
            }
            JsonObject obj = parsed.getAsJsonObject();

            if (obj.has("accepted") && !obj.get("accepted").isJsonNull() && !obj.get("accepted").getAsBoolean())
            {
                return "submission_not_accepted:" + trimForMessage(responseBody);
            }
            if (obj.has("ok") && !obj.get("ok").isJsonNull() && !obj.get("ok").getAsBoolean())
            {
                return "request_not_ok:" + trimForMessage(responseBody);
            }

            if (obj.has("sheet_mirror") && obj.get("sheet_mirror").isJsonObject())
            {
                JsonObject mirror = obj.getAsJsonObject("sheet_mirror");
                if (mirror.has("attempted") && mirror.has("ok"))
                {
                    boolean attempted = !mirror.get("attempted").isJsonNull() && mirror.get("attempted").getAsBoolean();
                    boolean ok = !mirror.get("ok").isJsonNull() && mirror.get("ok").getAsBoolean();
                    if (attempted && !ok)
                    {
                        return "sheet_mirror_failed:" + trimForMessage(mirror.toString());
                    }
                }
            }

            if (obj.has("processed") && obj.has("accepted"))
            {
                int processed = obj.get("processed").isJsonNull() ? 0 : obj.get("processed").getAsInt();
                int accepted = obj.get("accepted").isJsonNull() ? 0 : obj.get("accepted").getAsInt();
                if (processed > 0 && accepted < processed)
                {
                    return "bulk_partial_failure:" + trimForMessage(responseBody);
                }
            }
        }
        catch (Exception ignored)
        {
            return null;
        }
        return null;
    }

    private String trimForMessage(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        return raw.length() > 250 ? raw.substring(0, 250) : raw;
    }

    private String apiBaseUrl()
    {
        String configured = config.seasonalApiEndpoint();
        if (configured == null || configured.trim().isEmpty())
        {
            return DEFAULT_API_BASE_URL;
        }
        String base = configured.trim();
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        return base.isEmpty() ? DEFAULT_API_BASE_URL : base;
    }

    private void addBosses(JsonObject source, SeasonalEligibilityManifest manifest, String key)
    {
        if (!source.has(key) || !source.get(key).isJsonArray())
        {
            return;
        }
        JsonArray array = source.getAsJsonArray(key);
        for (int i = 0; i < array.size(); i++)
        {
            if (!array.get(i).isJsonNull())
            {
                manifest.getEligibleBossKeys().add(array.get(i).getAsString().trim().toLowerCase());
            }
        }
    }

    private void addItemIds(JsonObject source, SeasonalEligibilityManifest manifest, String key)
    {
        if (!source.has(key) || !source.get(key).isJsonArray())
        {
            return;
        }
        JsonArray array = source.getAsJsonArray(key);
        for (int i = 0; i < array.size(); i++)
        {
            if (!array.get(i).isJsonNull())
            {
                manifest.getEligibleItemIds().add(array.get(i).getAsInt());
            }
        }
    }
}

