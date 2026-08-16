package com.claneventlist;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Per-profile seasonal drop history under {@code .runelite/ironforged-clan-events/}.
 */
@Slf4j
@Singleton
public class SeasonalDropHistoryStore
{
    static final String PLUGIN_DIR = "ironforged-clan-events";
    private static final int MAX_HISTORY = 100;

    private final Gson gson;
    private volatile String profileKey = "local";
    private volatile List<SeasonalDropRecord> records = new ArrayList<>();

    @Inject
    public SeasonalDropHistoryStore(Gson gson)
    {
        this.gson = gson;
    }

    public synchronized void setProfileKey(String key)
    {
        String next = key == null || key.trim().isEmpty() ? "local" : sanitize(key.trim());
        if (next.equals(profileKey) && !records.isEmpty())
        {
            return;
        }
        profileKey = next;
        load();
    }

    public String getProfileKey()
    {
        return profileKey;
    }

    public synchronized List<SeasonalDropRecord> getRecords()
    {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public synchronized SeasonalDropRecord add(SeasonalDropRecord record)
    {
        if (record == null)
        {
            return null;
        }
        if (record.id == null || record.id.isEmpty())
        {
            record.id = UUID.randomUUID().toString();
        }
        if (record.savedAtEpochMs <= 0)
        {
            record.savedAtEpochMs = System.currentTimeMillis();
        }
        records.add(0, record);
        while (records.size() > MAX_HISTORY)
        {
            records.remove(records.size() - 1);
        }
        save();
        return record;
    }

    public synchronized void load()
    {
        Path path = historyPath();
        List<SeasonalDropRecord> next = new ArrayList<>();
        try
        {
            if (!Files.isRegularFile(path))
            {
                records = next;
                return;
            }
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = gson.fromJson(raw, JsonObject.class);
            if (root != null && root.has("records") && root.get("records").isJsonArray())
            {
                JsonArray arr = root.getAsJsonArray("records");
                for (JsonElement el : arr)
                {
                    if (!el.isJsonObject())
                    {
                        continue;
                    }
                    SeasonalDropRecord rec = fromJson(el.getAsJsonObject());
                    if (rec != null)
                    {
                        next.add(rec);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load drop history: {}", e.getMessage());
        }
        records = next;
    }

    private synchronized void save()
    {
        try
        {
            Path dir = RuneLite.RUNELITE_DIR.toPath().resolve(PLUGIN_DIR);
            Files.createDirectories(dir);
            JsonObject root = new JsonObject();
            root.addProperty("profile", profileKey);
            JsonArray arr = new JsonArray();
            for (SeasonalDropRecord rec : records)
            {
                arr.add(toJson(rec));
            }
            root.add("records", arr);
            Files.writeString(historyPath(), gson.toJson(root), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            log.warn("Failed to save drop history: {}", e.getMessage());
        }
    }

    private Path historyPath()
    {
        return RuneLite.RUNELITE_DIR.toPath()
            .resolve(PLUGIN_DIR)
            .resolve("history-" + profileKey + ".json");
    }

    private static String sanitize(String key)
    {
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static SeasonalDropRecord fromJson(JsonObject obj)
    {
        SeasonalDropRecord rec = new SeasonalDropRecord();
        rec.id = text(obj, "id");
        rec.status = text(obj, "status");
        rec.rsn = text(obj, "rsn");
        rec.clan = text(obj, "clan");
        rec.boss = text(obj, "boss");
        rec.itemId = obj.has("itemId") && obj.get("itemId").isJsonPrimitive()
            ? obj.get("itemId").getAsInt() : 0;
        rec.itemName = text(obj, "itemName");
        rec.quantity = obj.has("quantity") && obj.get("quantity").isJsonPrimitive()
            ? obj.get("quantity").getAsInt() : 1;
        rec.eventId = text(obj, "eventId");
        rec.date = text(obj, "date");
        rec.time = text(obj, "time");
        rec.droppedAt = text(obj, "droppedAt");
        rec.detail = text(obj, "detail");
        rec.savedAtEpochMs = obj.has("savedAtEpochMs") && obj.get("savedAtEpochMs").isJsonPrimitive()
            ? obj.get("savedAtEpochMs").getAsLong() : 0L;
        return rec;
    }

    private static JsonObject toJson(SeasonalDropRecord rec)
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", rec.id);
        obj.addProperty("status", rec.status);
        obj.addProperty("rsn", rec.rsn);
        obj.addProperty("clan", rec.clan);
        obj.addProperty("boss", rec.boss);
        obj.addProperty("itemId", rec.itemId);
        obj.addProperty("itemName", rec.itemName);
        obj.addProperty("quantity", rec.quantity);
        obj.addProperty("eventId", rec.eventId);
        obj.addProperty("date", rec.date);
        obj.addProperty("time", rec.time);
        obj.addProperty("droppedAt", rec.droppedAt);
        obj.addProperty("detail", rec.detail);
        obj.addProperty("savedAtEpochMs", rec.savedAtEpochMs);
        return obj;
    }

    private static String text(JsonObject obj, String key)
    {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
        {
            return "";
        }
        return obj.get(key).getAsString();
    }
}
