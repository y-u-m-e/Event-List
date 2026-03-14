package com.claneventlist;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class SeasonalSubmissionQueue
{
    private static final long BASE_BACKOFF_MS = 10_000L;
    private static final long MAX_BACKOFF_MS = 15 * 60_000L;
    private static final long DEDUPE_WINDOW_MS = 6 * 60 * 60_000L;

    private final Gson gson = new Gson();
    private final Path queuePath = RuneLite.RUNELITE_DIR.toPath().resolve("claneventlist-seasonal-queue.json");
    private final List<SeasonalQueueItem> queue = new ArrayList<>();
    private final List<SeasonalQueueItem> deadLetter = new ArrayList<>();
    private final Map<String, Long> dedupeExpiryByKey = new HashMap<>();

    public synchronized void loadFromDisk()
    {
        queue.clear();
        deadLetter.clear();
        if (!Files.exists(queuePath))
        {
            return;
        }

        try
        {
            String json = Files.readString(queuePath, StandardCharsets.UTF_8);
            Type type = new TypeToken<QueueSnapshot>() {}.getType();
            QueueSnapshot snapshot = gson.fromJson(json, type);
            if (snapshot != null)
            {
                if (snapshot.queue != null)
                {
                    queue.addAll(snapshot.queue);
                }
                if (snapshot.deadLetter != null)
                {
                    deadLetter.addAll(snapshot.deadLetter);
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("Failed loading seasonal queue: {}", ex.getMessage());
        }
    }

    public synchronized void saveToDisk()
    {
        try
        {
            QueueSnapshot snapshot = new QueueSnapshot();
            snapshot.queue = new ArrayList<>(queue);
            snapshot.deadLetter = new ArrayList<>(deadLetter);
            Files.writeString(queuePath, gson.toJson(snapshot), StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            log.warn("Failed saving seasonal queue: {}", ex.getMessage());
        }
    }

    public synchronized boolean enqueueIfNotDuplicate(SeasonalSubmission submission)
    {
        pruneDedupe();
        String key = dedupeKey(
            submission.getEventId(),
            submission.getEventPassphrase(),
            submission.getClientInstanceId(),
            submission.getBossKey(),
            submission.getItemId(),
            submission.getDroppedAt()
        );
        if (dedupeExpiryByKey.containsKey(key))
        {
            return false;
        }

        dedupeExpiryByKey.put(key, System.currentTimeMillis() + DEDUPE_WINDOW_MS);
        queue.add(new SeasonalQueueItem(submission, 0, 0, ""));
        return true;
    }

    public synchronized List<SeasonalQueueItem> pollReady(int maxItems)
    {
        long now = System.currentTimeMillis();
        List<SeasonalQueueItem> ready = new ArrayList<>();
        Iterator<SeasonalQueueItem> it = queue.iterator();
        while (it.hasNext() && ready.size() < maxItems)
        {
            SeasonalQueueItem item = it.next();
            if (item.getNextAttemptEpochMs() <= now)
            {
                ready.add(item);
                it.remove();
            }
        }
        return ready;
    }

    public synchronized void markRetry(SeasonalQueueItem item, String reason)
    {
        int attempts = item.getAttempts() + 1;
        item.setAttempts(attempts);
        item.setLastError(reason);
        item.setNextAttemptEpochMs(System.currentTimeMillis() + backoffMillis(attempts));
        queue.add(item);
    }

    public synchronized void markDeadLetter(SeasonalQueueItem item, String reason)
    {
        item.setLastError(reason);
        deadLetter.add(item);
    }

    public synchronized void requeueFront(List<SeasonalQueueItem> items, String reason)
    {
        for (SeasonalQueueItem item : items)
        {
            markRetry(item, reason);
        }
    }

    public synchronized void clearQueue()
    {
        queue.clear();
    }

    public synchronized int queueSize()
    {
        return queue.size();
    }

    public synchronized int deadLetterSize()
    {
        return deadLetter.size();
    }

    public static long backoffMillis(int attempts)
    {
        long backoff = BASE_BACKOFF_MS * (1L << Math.min(attempts, 10));
        return Math.min(backoff, MAX_BACKOFF_MS);
    }

    public static String dedupeKey(
        String eventId,
        String eventPassphrase,
        String clientInstanceId,
        String bossKey,
        int itemId,
        String droppedAtIso
    )
    {
        Instant instant = Instant.parse(droppedAtIso);
        ZonedDateTime bucketHour = instant.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        return eventId + "|" + eventPassphrase + "|" + clientInstanceId + "|" + bossKey + "|" + itemId + "|"
            + bucketHour.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private void pruneDedupe()
    {
        long now = System.currentTimeMillis();
        dedupeExpiryByKey.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private static class QueueSnapshot
    {
        private List<SeasonalQueueItem> queue;
        private List<SeasonalQueueItem> deadLetter;
    }
}

