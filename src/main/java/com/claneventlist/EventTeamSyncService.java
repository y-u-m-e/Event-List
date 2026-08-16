package com.claneventlist;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in sync of event team rosters from {@code GET /plugins/seasonal/teams}.
 * Overwrites local team count / names / colors when a payload is accepted.
 */
@Slf4j
@Singleton
public class EventTeamSyncService
{
    private final EventListConfig config;
    private final ConfigManager configManager;
    private final SeasonalCatalogService catalogService;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private volatile long lastFetchTime;
    private volatile String lastStatus = "Team sync idle";
    private volatile String lastError = "";

    @Inject
    EventTeamSyncService(
        EventListConfig config,
        ConfigManager configManager,
        SeasonalCatalogService catalogService,
        OkHttpClient httpClient,
        Gson gson
    )
    {
        this.config = config;
        this.configManager = configManager;
        this.catalogService = catalogService;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public String getLastStatus()
    {
        return lastStatus;
    }

    public String getLastError()
    {
        return lastError;
    }

    public boolean needsRefresh(int intervalMinutes)
    {
        if (!config.enableTeamRosterSync())
        {
            return false;
        }
        long intervalMs = Math.max(1, intervalMinutes) * 60_000L;
        return System.currentTimeMillis() - lastFetchTime >= intervalMs;
    }

    /**
     * Fetch and apply team roster. Safe to call off the client thread.
     *
     * @return true if local config was updated
     */
    public synchronized boolean syncNow()
    {
        if (!config.enableTeamRosterSync())
        {
            lastStatus = "Team sync disabled";
            return false;
        }

        String eventId = catalogService.resolveEventId(config);
        String base = EventsApiService.resolveApiBase(config) + "/plugins/seasonal/teams";
        HttpUrl url = HttpUrl.parse(base);
        if (url == null)
        {
            lastError = "bad url";
            lastStatus = "Team sync failed: bad url";
            return false;
        }
        HttpUrl.Builder urlBuilder = url.newBuilder();
        if (!eventId.isEmpty())
        {
            urlBuilder.addQueryParameter("event_id", eventId);
        }

        Request.Builder req = new Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "RuneLite-IronforgedClanEvents")
            .header("Accept", "application/json")
            .get();

        String passphrase = catalogService.resolveEventPassphrase(config);
        if (!passphrase.isEmpty())
        {
            req.header("X-Event-Passphrase", passphrase);
        }

        try (Response response = httpClient.newCall(req.build()).execute())
        {
            lastFetchTime = System.currentTimeMillis();
            if (!response.isSuccessful() || response.body() == null)
            {
                lastError = "HTTP " + response.code();
                lastStatus = "Team sync failed: " + lastError;
                log.warn("Team roster sync failed: {}", lastError);
                return false;
            }

            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null)
            {
                lastError = "empty payload";
                lastStatus = "Team sync failed: empty payload";
                return false;
            }

            JsonArray teams = root.has("teams") && root.get("teams").isJsonArray()
                ? root.getAsJsonArray("teams")
                : null;
            if (teams == null || teams.size() == 0)
            {
                lastError = "no teams";
                lastStatus = "Team sync: API returned no teams";
                return false;
            }

            List<SyncedTeam> syncedTeams = new ArrayList<>();
            for (JsonElement el : teams)
            {
                if (!el.isJsonObject())
                {
                    continue;
                }
                SyncedTeam team = parseTeam(el.getAsJsonObject(), syncedTeams.size() + 1);
                if (team != null)
                {
                    syncedTeams.add(team);
                }
                if (syncedTeams.size() >= EventTeamService.MAX_TEAMS)
                {
                    break;
                }
            }

            if (syncedTeams.isEmpty())
            {
                lastError = "unparseable teams";
                lastStatus = "Team sync failed: could not parse teams";
                return false;
            }

            applyTeams(syncedTeams);
            lastError = "";
            String apiEvent = text(root, "event_id");
            lastStatus = "Synced " + syncedTeams.size() + " team(s)"
                + (apiEvent.isEmpty() ? "" : " · " + apiEvent);
            log.debug("Team roster sync applied {} teams", syncedTeams.size());
            return true;
        }
        catch (Exception e)
        {
            lastError = e.getMessage() == null ? "error" : e.getMessage();
            lastStatus = "Team sync failed: " + lastError;
            log.warn("Team roster sync error: {}", lastError);
            return false;
        }
    }

    private void applyTeams(List<SyncedTeam> teams)
    {
        int count = Math.min(teams.size(), EventTeamService.MAX_TEAMS);
        configManager.setConfiguration(EventListConfig.CONFIG_GROUP, "teamCount", count);

        for (int i = 0; i < count; i++)
        {
            SyncedTeam team = teams.get(i);
            int slot = i + 1;
            configManager.setConfiguration(
                EventListConfig.CONFIG_GROUP,
                EventTeamService.teamNamesKey(slot),
                String.join("\n", team.members));
            if (team.colorKey != null && !team.colorKey.isEmpty())
            {
                configManager.setConfiguration(
                    EventListConfig.CONFIG_GROUP,
                    EventTeamService.teamColorKey(slot),
                    team.colorKey);
            }
            if (team.name != null && !team.name.isEmpty())
            {
                configManager.setConfiguration(
                    EventListConfig.CONFIG_GROUP,
                    EventTeamService.teamLabelKey(slot),
                    team.name);
            }
        }

        // Clear leftover slots beyond synced count so stale RSNs do not linger.
        for (int slot = count + 1; slot <= EventTeamService.MAX_TEAMS; slot++)
        {
            configManager.unsetConfiguration(EventListConfig.CONFIG_GROUP, EventTeamService.teamNamesKey(slot));
            configManager.unsetConfiguration(EventListConfig.CONFIG_GROUP, EventTeamService.teamLabelKey(slot));
        }
    }

    private static SyncedTeam parseTeam(JsonObject obj, int fallbackSlot)
    {
        SyncedTeam team = new SyncedTeam();
        team.slot = obj.has("slot") && obj.get("slot").isJsonPrimitive()
            ? obj.get("slot").getAsInt()
            : fallbackSlot;
        team.name = text(obj, "name");
        if (team.name.isEmpty())
        {
            team.name = text(obj, "label");
        }
            String color = text(obj, "color");
            EventTeamIconFactory.TeamPalette palette = EventTeamIconFactory.TeamPalette.fromKey(color);
            if (palette == null && color.matches("(?i)^#[0-9A-F]{6}$"))
            {
                try
                {
                    palette = EventTeamIconFactory.TeamPalette.nearest(java.awt.Color.decode(color));
                }
                catch (NumberFormatException ignored)
                {
                    palette = null;
                }
            }
            if (palette != null)
            {
                team.colorKey = palette.getKey();
            }

        JsonArray members = null;
        if (obj.has("members") && obj.get("members").isJsonArray())
        {
            members = obj.getAsJsonArray("members");
        }
        else if (obj.has("players") && obj.get("players").isJsonArray())
        {
            members = obj.getAsJsonArray("players");
        }
        if (members == null)
        {
            return null;
        }

        for (JsonElement m : members)
        {
            String name;
            if (m.isJsonPrimitive())
            {
                name = m.getAsString();
            }
            else if (m.isJsonObject())
            {
                name = text(m.getAsJsonObject(), "rsn");
                if (name.isEmpty())
                {
                    name = text(m.getAsJsonObject(), "name");
                }
            }
            else
            {
                continue;
            }
            name = name == null ? "" : name.trim();
            if (!name.isEmpty())
            {
                team.members.add(name);
            }
        }
        return team;
    }

    private static String text(JsonObject obj, String key)
    {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
        {
            return "";
        }
        return obj.get(key).getAsString().trim();
    }

    private static final class SyncedTeam
    {
        int slot;
        String name = "";
        String colorKey = "";
        final List<String> members = new ArrayList<>();
    }
}
