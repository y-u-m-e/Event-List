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
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Disk-backed retry queue for failed seasonal drop uploads.
 */
@Slf4j
@Singleton
public class SeasonalSubmitQueue
{
    private static final int MAX_QUEUE = 20;
    private static final int MAX_ATTEMPTS = 8;

    private final Gson gson;
    private final List<QueuedDrop> queue = new ArrayList<>();

    @Inject
    public SeasonalSubmitQueue(Gson gson)
    {
        this.gson = gson;
    }

    public synchronized int size()
    {
        return queue.size();
    }

    public synchronized void load()
    {
        queue.clear();
        Path path = queuePath();
        try
        {
            if (!Files.isRegularFile(path))
            {
                return;
            }
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = gson.fromJson(raw, JsonObject.class);
            if (root == null || !root.has("items") || !root.get("items").isJsonArray())
            {
                return;
            }
            for (JsonElement el : root.getAsJsonArray("items"))
            {
                if (!el.isJsonObject())
                {
                    continue;
                }
                QueuedDrop item = QueuedDrop.fromJson(el.getAsJsonObject());
                if (item != null && item.screenshotBase64 != null && !item.screenshotBase64.isEmpty())
                {
                    queue.add(item);
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load submit queue: {}", e.getMessage());
        }
    }

    public synchronized void enqueue(QueuedDrop item)
    {
        if (item == null || item.screenshotBase64 == null || item.screenshotBase64.isEmpty())
        {
            return;
        }
        if (item.id == null || item.id.isEmpty())
        {
            item.id = UUID.randomUUID().toString();
        }
        // Replace same logical drop if already queued.
        queue.removeIf(existing -> sameDrop(existing, item));
        queue.add(0, item);
        while (queue.size() > MAX_QUEUE)
        {
            queue.remove(queue.size() - 1);
        }
        save();
    }

    public synchronized List<QueuedDrop> snapshotReady()
    {
        return new ArrayList<>(queue);
    }

    public synchronized void markSuccess(String id)
    {
        queue.removeIf(item -> id.equals(item.id));
        save();
    }

    public synchronized void markAttempt(String id, String error, boolean retryable)
    {
        for (QueuedDrop item : queue)
        {
            if (!id.equals(item.id))
            {
                continue;
            }
            item.attempts += 1;
            item.lastError = error == null ? "" : error;
            item.lastAttemptEpochMs = System.currentTimeMillis();
            if (!retryable || item.attempts >= MAX_ATTEMPTS)
            {
                queue.remove(item);
            }
            save();
            return;
        }
    }

    private synchronized void save()
    {
        try
        {
            Path dir = RuneLite.RUNELITE_DIR.toPath().resolve(SeasonalDropHistoryStore.PLUGIN_DIR);
            Files.createDirectories(dir);
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (QueuedDrop item : queue)
            {
                arr.add(item.toJson());
            }
            root.add("items", arr);
            Files.writeString(queuePath(), gson.toJson(root), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            log.warn("Failed to save submit queue: {}", e.getMessage());
        }
    }

    private static Path queuePath()
    {
        return RuneLite.RUNELITE_DIR.toPath()
            .resolve(SeasonalDropHistoryStore.PLUGIN_DIR)
            .resolve("submit-queue.json");
    }

    private static boolean sameDrop(QueuedDrop a, QueuedDrop b)
    {
        return a.eventId.equals(b.eventId)
            && a.boss.equals(b.boss)
            && a.itemId == b.itemId
            && a.itemName.equals(b.itemName)
            && a.droppedAt.equals(b.droppedAt);
    }

    static final class QueuedDrop
    {
        String id = "";
        String rsn = "";
        String clan = "";
        String boss = "";
        int itemId;
        String itemName = "";
        int quantity = 1;
        String eventId = "";
        String eventPassphrase = "";
        String date = "";
        String time = "";
        String droppedAt = "";
        String screenshotBase64 = "";
        int attempts;
        String lastError = "";
        long createdAtEpochMs = System.currentTimeMillis();
        long lastAttemptEpochMs;

        byte[] screenshotBytes()
        {
            try
            {
                return Base64.getDecoder().decode(screenshotBase64);
            }
            catch (Exception e)
            {
                return new byte[0];
            }
        }

        static QueuedDrop fromPayload(
            String rsn,
            String clan,
            String boss,
            int itemId,
            String itemName,
            int quantity,
            String eventId,
            String eventPassphrase,
            String date,
            String time,
            String droppedAt,
            byte[] screenshot
        )
        {
            QueuedDrop item = new QueuedDrop();
            item.id = UUID.randomUUID().toString();
            item.rsn = rsn;
            item.clan = clan;
            item.boss = boss;
            item.itemId = itemId;
            item.itemName = itemName;
            item.quantity = quantity;
            item.eventId = eventId;
            item.eventPassphrase = eventPassphrase;
            item.date = date;
            item.time = time;
            item.droppedAt = droppedAt;
            item.screenshotBase64 = Base64.getEncoder().encodeToString(screenshot);
            item.createdAtEpochMs = System.currentTimeMillis();
            return item;
        }

        JsonObject toJson()
        {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("rsn", rsn);
            obj.addProperty("clan", clan);
            obj.addProperty("boss", boss);
            obj.addProperty("itemId", itemId);
            obj.addProperty("itemName", itemName);
            obj.addProperty("quantity", quantity);
            obj.addProperty("eventId", eventId);
            obj.addProperty("eventPassphrase", eventPassphrase);
            obj.addProperty("date", date);
            obj.addProperty("time", time);
            obj.addProperty("droppedAt", droppedAt);
            obj.addProperty("screenshotBase64", screenshotBase64);
            obj.addProperty("attempts", attempts);
            obj.addProperty("lastError", lastError);
            obj.addProperty("createdAtEpochMs", createdAtEpochMs);
            obj.addProperty("lastAttemptEpochMs", lastAttemptEpochMs);
            return obj;
        }

        static QueuedDrop fromJson(JsonObject obj)
        {
            QueuedDrop item = new QueuedDrop();
            item.id = text(obj, "id");
            item.rsn = text(obj, "rsn");
            item.clan = text(obj, "clan");
            item.boss = text(obj, "boss");
            item.itemId = obj.has("itemId") ? obj.get("itemId").getAsInt() : 0;
            item.itemName = text(obj, "itemName");
            item.quantity = obj.has("quantity") ? obj.get("quantity").getAsInt() : 1;
            item.eventId = text(obj, "eventId");
            item.eventPassphrase = text(obj, "eventPassphrase");
            item.date = text(obj, "date");
            item.time = text(obj, "time");
            item.droppedAt = text(obj, "droppedAt");
            item.screenshotBase64 = text(obj, "screenshotBase64");
            item.attempts = obj.has("attempts") ? obj.get("attempts").getAsInt() : 0;
            item.lastError = text(obj, "lastError");
            item.createdAtEpochMs = obj.has("createdAtEpochMs") ? obj.get("createdAtEpochMs").getAsLong() : 0L;
            item.lastAttemptEpochMs = obj.has("lastAttemptEpochMs") ? obj.get("lastAttemptEpochMs").getAsLong() : 0L;
            return item;
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
}
