package com.claneventlist;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.DrawManager;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Captures notable seasonal drops only (catalog-gated) and POSTs them to the public API.
 * Common loot never leaves the client. Failed uploads are queued for retry.
 */
@Slf4j
@Singleton
public class SeasonalDropSubmitService
{
    private static final String CLIENT_ID = "runelite-ironforged-clan-events";
    private static final MediaType JPEG = MediaType.parse("image/jpeg");
    private static final int MAX_RECENT_DEDUPE = 64;
    private static final int MAX_SCREENSHOT_WIDTH = 1280;
    private static final float JPEG_QUALITY = 0.72f;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EventListConfig config;
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private final DrawManager drawManager;
    private final OkHttpClient httpClient;
    private final SeasonalCatalogService catalogService;
    private final SeasonalSubmitQueue submitQueue;
    private final SeasonalDropHistoryStore historyStore;
    private final ChatMessageManager chatMessageManager;
    private final ScheduledExecutorService executorService;
    private final Gson gson;

    private final Set<String> recentDedupe = new LinkedHashSet<>();

    private volatile String lastStatus = "Seasonal submit idle";
    private volatile Runnable statusListener;
    private volatile boolean processingQueue;

    @Inject
    public SeasonalDropSubmitService(
        EventListConfig config,
        Client client,
        ClientThread clientThread,
        ItemManager itemManager,
        DrawManager drawManager,
        OkHttpClient httpClient,
        SeasonalCatalogService catalogService,
        SeasonalSubmitQueue submitQueue,
        SeasonalDropHistoryStore historyStore,
        ChatMessageManager chatMessageManager,
        ScheduledExecutorService executorService,
        Gson gson
    )
    {
        this.config = config;
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
        this.drawManager = drawManager;
        this.httpClient = httpClient;
        this.catalogService = catalogService;
        this.submitQueue = submitQueue;
        this.historyStore = historyStore;
        this.chatMessageManager = chatMessageManager;
        this.executorService = executorService;
        this.gson = gson;
    }

    public void start()
    {
        String key = profileKey();
        executorService.execute(() ->
        {
            submitQueue.load();
            if (!historyStore.setProfileKey(key))
            {
                historyStore.load();
            }
            int queued = submitQueue.size();
            setStatus(queued > 0
                ? "Seasonal submit ready · " + queued + " queued"
                : "Seasonal submit idle");
        });
    }

    /**
     * Reads the account hash on the calling (client) thread; the history reload runs on the executor.
     */
    public void refreshProfileKey()
    {
        String key = profileKey();
        executorService.execute(() ->
        {
            if (historyStore.setProfileKey(key))
            {
                setStatus(lastStatus);
            }
        });
    }

    private String profileKey()
    {
        long hash = client.getAccountHash();
        return hash != -1L && hash != 0L ? "rs-" + Long.toUnsignedString(hash) : "local";
    }

    public void setStatusListener(Runnable statusListener)
    {
        this.statusListener = statusListener;
    }

    public String getLastStatus()
    {
        return lastStatus;
    }

    public int getQueueSize()
    {
        return submitQueue.size();
    }

    public List<SeasonalDropRecord> getHistory()
    {
        return historyStore.getRecords();
    }

    private void setStatus(String status)
    {
        lastStatus = status == null ? "" : status;
        Runnable listener = statusListener;
        if (listener != null)
        {
            listener.run();
        }
    }

    public void onNpcLootReceived(NpcLootReceived event)
    {
        NPC npc = event.getNpc();
        String bossName = npc != null ? npc.getName() : null;
        handleLoot(bossName, event.getItems());
    }

    public void onPlayerLootReceived(PlayerLootReceived event)
    {
        Player player = event.getPlayer();
        String bossName = player != null ? player.getName() : null;
        handleLoot(bossName, event.getItems());
    }

    /**
     * Raids, Barrows, Gauntlet and other chests. LootReceived only fires when RuneLite's
     * Loot Tracker plugin is enabled. NPC/PLAYER types are skipped: those already arrive
     * via NpcLootReceived / PlayerLootReceived.
     */
    public void onLootReceived(LootReceived event)
    {
        if (event.getType() != LootRecordType.EVENT)
        {
            return;
        }
        handleLoot(event.getName(), event.getItems());
    }

    private void handleLoot(String sourceName, Collection<ItemStack> items)
    {
        if (!config.enableSeasonalSubmit() || items == null || items.isEmpty())
        {
            return;
        }

        String eventId = catalogService.resolveEventId(config);
        String eventPassphrase = catalogService.resolveEventPassphrase(config);
        if (eventId.isEmpty())
        {
            setStatus("Seasonal submit: set Seasonal Event ID");
            return;
        }
        if (eventPassphrase.isEmpty())
        {
            setStatus("Seasonal submit: set Seasonal Event Passphrase");
            return;
        }
        if (!catalogService.canSubmitAgainstCatalog())
        {
            setStatus(catalogService.isAcceptingSubmissions()
                ? "Seasonal submit: catalog empty / unavailable"
                : "Seasonal submit: not accepting submissions");
            return;
        }
        if (sourceName == null || sourceName.trim().isEmpty())
        {
            return;
        }

        String rsn = localRsn();
        if (rsn.isEmpty())
        {
            setStatus("Seasonal submit: could not read RSN");
            return;
        }
        // Read here on the client thread; the upload runs on the executor.
        String clan = localClanName();

        List<PendingDrop> matches = new ArrayList<>();
        for (ItemStack stack : items)
        {
            String itemName = resolveItemName(stack.getId());
            if (itemName.isEmpty())
            {
                continue;
            }
            SeasonalCatalogEntry match = catalogService.findMatch(sourceName, itemName, stack.getId());
            if (match == null)
            {
                continue;
            }
            // Tick is part of the key so two identical drops on different kills both count,
            // while the same stack reported twice in one tick (e.g. duplicate loot events) does not.
            String dedupeKey = client.getAccountHash() + ":" + client.getWorld() + ":"
                + client.getTickCount() + ":" + stack.getId() + ":" + stack.getQuantity();
            if (!rememberDedupe(dedupeKey))
            {
                continue;
            }
            matches.add(new PendingDrop(match, stack.getId(), stack.getQuantity()));
        }

        if (matches.isEmpty())
        {
            return;
        }

        setStatus("Seasonal submit: capturing " + matches.size() + " notable drop(s)");
        if (config.seasonalSubmitDryRun())
        {
            PendingDrop first = matches.get(0);
            setStatus("Dry run: would submit " + first.entry.getBoss() + " / " + first.entry.getItem());
            notifyGameDrop("Dry run", first.entry.getBoss(), first.entry.getItem(), null);
            return;
        }

        captureScreenshotAndSubmit(rsn, clan, eventId, eventPassphrase, matches, false);
    }

    /**
     * Posts the first catalog entry to the dev API. Reads RSN / clan on the client thread first.
     */
    public void submitTestDrop()
    {
        clientThread.invoke(() ->
        {
            String rsn = localRsn();
            String clan = localClanName();
            executorService.execute(() -> submitTestDrop(rsn.isEmpty() ? "TestPlayer" : rsn, clan));
        });
    }

    private void submitTestDrop(String rsn, String clan)
    {
        if (!config.enableSeasonalSubmit())
        {
            setStatus("Test submit: enable Submit notable seasonal drops first");
            return;
        }
        // The catalog, event ID and passphrase come from the configured API; they only
        // match the dev API the test posts to when Use dev API is on.
        if (!config.useDevApi())
        {
            setStatus("Test submit: turn on Use dev API first (tests only go to dev)");
            return;
        }

        String eventId = catalogService.resolveEventId(config);
        String eventPassphrase = catalogService.resolveEventPassphrase(config);
        if (eventId.isEmpty())
        {
            setStatus("Test submit: set Seasonal Event ID");
            return;
        }
        if (eventPassphrase.isEmpty())
        {
            setStatus("Test submit: set Seasonal Event Passphrase");
            return;
        }
        if (!catalogService.canSubmitAgainstCatalog())
        {
            setStatus("Test submit: catalog empty — refresh allowed drops first");
            return;
        }

        List<SeasonalCatalogEntry> catalog = catalogService.getCatalog();
        SeasonalCatalogEntry entry = catalog.get(0);

        if (config.seasonalSubmitDryRun())
        {
            setStatus("Dry run: would test-submit " + entry.getBoss() + " / " + entry.getItem());
            notifyGameDrop("Dry run", entry.getBoss(), entry.getItem(), null);
            return;
        }

        setStatus("Test submit: capturing screenshot for " + entry.getBoss() + " / " + entry.getItem());
        List<PendingDrop> drops = new ArrayList<>();
        drops.add(new PendingDrop(entry, 0, 1));
        captureScreenshotAndSubmit(rsn, clan, eventId, eventPassphrase, drops, true);
    }

    public void processQueue()
    {
        if (!config.enableSeasonalSubmit() || config.seasonalSubmitDryRun() || processingQueue)
        {
            return;
        }
        List<SeasonalSubmitQueue.QueuedDrop> ready = submitQueue.snapshotReady();
        if (ready.isEmpty())
        {
            return;
        }
        processingQueue = true;
        executorService.submit(() ->
        {
            try
            {
                for (SeasonalSubmitQueue.QueuedDrop item : ready)
                {
                    // Back off briefly between attempts.
                    if (item.lastAttemptEpochMs > 0
                        && System.currentTimeMillis() - item.lastAttemptEpochMs < 15_000L)
                    {
                        continue;
                    }
                    UploadResult result = uploadQueued(item);
                    if (result.success)
                    {
                        submitQueue.markSuccess(item.id);
                        recordHistory(item, "submitted", "Retried OK");
                        notifyGameDrop("Submitted", item.boss, item.itemName, "retry");
                        setStatus("Retry OK: " + item.boss + " / " + item.itemName
                            + " · queue " + submitQueue.size());
                    }
                    else
                    {
                        boolean dropped = submitQueue.markAttempt(item.id, result.message, result.retryable);
                        if (dropped)
                        {
                            String reason = result.retryable
                                ? "Gave up after 24h: " + truncate(result.message, 80)
                                : result.message;
                            recordHistory(item, "failed", reason);
                            notifyGameDrop("Failed", item.boss, item.itemName, truncate(reason, 60));
                        }
                        setStatus("Retry " + (dropped ? "gave up" : "scheduled")
                            + ": " + truncate(result.message, 80)
                            + " · queue " + submitQueue.size());
                    }
                }
            }
            finally
            {
                processingQueue = false;
                setStatus(lastStatus);
            }
        });
    }

    /**
     * Client thread registers the frame listener; the listener copies the frame on the
     * render thread (the image is only valid there); scaling, JPEG encode and upload run
     * on the executor.
     *
     * @param test test submit: allows a placeholder image, always targets the dev API, never queues
     */
    private void captureScreenshotAndSubmit(
        String rsn,
        String clan,
        String eventId,
        String eventPassphrase,
        List<PendingDrop> drops,
        boolean test
    )
    {
        setStatus("Seasonal submit: queued (capture next frame)");
        clientThread.invoke(() -> drawManager.requestNextFrameListener(image ->
        {
            BufferedImage copy = copyImage(image);
            if (copy == null && test)
            {
                copy = tinyImage();
            }
            if (copy == null)
            {
                setStatus("Seasonal submit: screenshot failed");
                return;
            }

            final BufferedImage frame = copy;
            executorService.submit(() ->
            {
                byte[] jpeg = encodeJpeg(scaleForUpload(frame));
                if ((jpeg == null || jpeg.length == 0) && test)
                {
                    jpeg = encodeJpeg(tinyImage());
                }
                if (jpeg == null || jpeg.length == 0)
                {
                    setStatus("Seasonal submit: screenshot encode failed");
                    return;
                }

                setStatus("Seasonal submit: uploading (" + jpeg.length + " bytes)");
                for (PendingDrop drop : drops)
                {
                    postDrop(rsn, clan, eventId, eventPassphrase, drop, jpeg, test);
                }
            });
        }));
    }

    private void postDrop(
        String rsn,
        String clan,
        String eventId,
        String eventPassphrase,
        PendingDrop drop,
        byte[] screenshotBytes,
        boolean test
    )
    {
        LocalDateTime now = LocalDateTime.now();
        String date = LocalDate.now().format(DATE_FMT);
        String time = LocalTime.now().format(TIME_FMT);
        String droppedAt = now.toString();
        String boss = drop.entry.getBoss();
        String itemName = drop.entry.getItem();
        int itemId = drop.itemId;

        UploadResult result = upload(
            rsn, clan, boss, itemId, itemName, drop.quantity,
            eventId, eventPassphrase, date, time, droppedAt, screenshotBytes, test
        );

        if (result.success)
        {
            SeasonalDropRecord rec = new SeasonalDropRecord();
            rec.status = "submitted";
            rec.rsn = rsn;
            rec.clan = clan;
            rec.boss = boss;
            rec.itemId = itemId;
            rec.itemName = itemName;
            rec.quantity = drop.quantity;
            rec.eventId = eventId;
            rec.date = date;
            rec.time = time;
            rec.droppedAt = droppedAt;
            rec.detail = screenshotBytes.length + " bytes" + (test ? " · test (dev API)" : "");
            historyStore.add(rec);
            notifyGameDrop("Submitted", boss, itemName, null);
            setStatus("Submitted: " + boss + " / " + itemName
                + " (" + screenshotBytes.length + " byte screenshot) [ID " + eventId + "]");
            return;
        }

        // Test submits never queue: a retry would go to the configured (possibly prod) API.
        if (result.retryable && !test)
        {
            SeasonalSubmitQueue.QueuedDrop queued = SeasonalSubmitQueue.QueuedDrop.fromPayload(
                rsn, clan, boss, itemId, itemName, drop.quantity,
                eventId, eventPassphrase, date, time, droppedAt, screenshotBytes
            );
            SeasonalDropRecord rec = new SeasonalDropRecord();
            rec.status = "queued";
            rec.rsn = rsn;
            rec.clan = clan;
            rec.boss = boss;
            rec.itemId = itemId;
            rec.itemName = itemName;
            rec.quantity = drop.quantity;
            rec.eventId = eventId;
            rec.date = date;
            rec.time = time;
            rec.droppedAt = droppedAt;
            rec.detail = "Queued: " + truncate(result.message, 80);
            queued.historyId = historyStore.add(rec).id;
            submitQueue.enqueue(queued);
            notifyGameDrop("Queued", boss, itemName, null);
            setStatus("Queued for retry: " + truncate(result.message, 100)
                + " · queue " + submitQueue.size());
        }
        else
        {
            SeasonalDropRecord rec = new SeasonalDropRecord();
            rec.status = "failed";
            rec.rsn = rsn;
            rec.clan = clan;
            rec.boss = boss;
            rec.itemId = itemId;
            rec.itemName = itemName;
            rec.quantity = drop.quantity;
            rec.eventId = eventId;
            rec.date = date;
            rec.time = time;
            rec.droppedAt = droppedAt;
            rec.detail = truncate(result.message, 120);
            historyStore.add(rec);
            notifyGameDrop("Failed", boss, itemName, truncate(result.message, 60));
            setStatus("Submit failed: " + truncate(result.message, 120));
        }
    }

    private UploadResult uploadQueued(SeasonalSubmitQueue.QueuedDrop item)
    {
        return upload(
            item.rsn, item.clan, item.boss, item.itemId, item.itemName, item.quantity,
            item.eventId, item.eventPassphrase, item.date, item.time, item.droppedAt,
            item.screenshotBytes(), false
        );
    }

    private UploadResult upload(
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
        byte[] screenshotBytes,
        boolean test
    )
    {
        if (screenshotBytes == null || screenshotBytes.length == 0)
        {
            return UploadResult.fatal("missing screenshot bytes");
        }

        JsonObject data = new JsonObject();
        data.addProperty("rsn", nullToEmpty(rsn));
        data.addProperty("clan", nullToEmpty(clan));
        data.addProperty("boss", nullToEmpty(boss));
        data.addProperty("item_id", itemId);
        data.addProperty("item_name", nullToEmpty(itemName));
        data.addProperty("date", nullToEmpty(date));
        data.addProperty("time", nullToEmpty(time));
        data.addProperty("dropped_at", nullToEmpty(droppedAt));
        data.addProperty("event_id", nullToEmpty(eventId));
        data.addProperty("event_passphrase", nullToEmpty(eventPassphrase));
        data.addProperty("seasonal_event_id", nullToEmpty(eventId));
        data.addProperty("seasonal_event_passphrase", nullToEmpty(eventPassphrase));
        data.addProperty("quantity", quantity);
        data.addProperty("screenshot", true);
        if (test)
        {
            data.addProperty("test", true);
        }

        RequestBody fileBody = RequestBody.create(JPEG, screenshotBytes);
        MultipartBody.Builder form = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("rsn", nullToEmpty(rsn))
            .addFormDataPart("clan", nullToEmpty(clan))
            .addFormDataPart("boss", nullToEmpty(boss))
            .addFormDataPart("item", nullToEmpty(itemName))
            .addFormDataPart("item_name", nullToEmpty(itemName))
            .addFormDataPart("item_id", String.valueOf(itemId))
            .addFormDataPart("date", nullToEmpty(date))
            .addFormDataPart("time", nullToEmpty(time))
            .addFormDataPart("dropped_at", nullToEmpty(droppedAt))
            .addFormDataPart("event_id", nullToEmpty(eventId))
            .addFormDataPart("seasonal_event_id", nullToEmpty(eventId))
            .addFormDataPart("event_passphrase", nullToEmpty(eventPassphrase))
            .addFormDataPart("seasonal_event_passphrase", nullToEmpty(eventPassphrase))
            .addFormDataPart("passphrase", nullToEmpty(eventPassphrase))
            .addFormDataPart("client", CLIENT_ID)
            .addFormDataPart("data", gson.toJson(data))
            .addFormDataPart("screenshot", "drop.jpg", fileBody);
        if (test)
        {
            form.addFormDataPart("test", "true");
        }
        RequestBody body = form.build();

        // Test submits always go to dev, whatever the Use dev API toggle says.
        String apiBase = test ? EventsApiService.DEV_API_BASE : EventsApiService.resolveApiBase(config);
        Request request = new Request.Builder()
            .url(apiBase + "/plugins/seasonal/drops")
            .header("User-Agent", "RuneLite-IronforgedClanEvents")
            .header("X-Event-Passphrase", nullToEmpty(eventPassphrase))
            .header("X-Seasonal-Event-Passphrase", nullToEmpty(eventPassphrase))
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful())
            {
                return UploadResult.ok();
            }
            int code = response.code();
            boolean retryable = code == 408 || code == 429 || code >= 500;
            return new UploadResult(false, retryable, "HTTP " + code + ": " + truncate(responseBody, 120));
        }
        catch (Exception e)
        {
            return new UploadResult(false, true, e.getMessage() == null ? "network error" : e.getMessage());
        }
    }

    private void recordHistory(SeasonalSubmitQueue.QueuedDrop item, String status, String detail)
    {
        SeasonalDropRecord rec = new SeasonalDropRecord();
        rec.status = status;
        rec.rsn = item.rsn;
        rec.clan = item.clan;
        rec.boss = item.boss;
        rec.itemId = item.itemId;
        rec.itemName = item.itemName;
        rec.quantity = item.quantity;
        rec.eventId = item.eventId;
        rec.date = item.date;
        rec.time = item.time;
        rec.droppedAt = item.droppedAt;
        rec.detail = detail == null ? "" : detail;
        // Prefer updating the "queued" row written when the drop was first queued.
        if (!historyStore.update(item.historyId, rec.status, rec.detail))
        {
            historyStore.add(rec);
        }
    }

    private void notifyGameDrop(String action, String boss, String itemName, String detail)
    {
        String safeBoss = boss == null || boss.isEmpty() ? "?" : boss;
        String safeItem = itemName == null || itemName.isEmpty() ? "?" : itemName;
        // Darker muted gold / slate so Drop/Boss don't overpower the brand prefix.
        StringBuilder msg = new StringBuilder();
        msg.append(action);
        msg.append(" — Drop: <col=7a6436>").append(escapeCol(safeItem)).append("</col>");
        msg.append(" · Boss: <col=3f5f73>").append(escapeCol(safeBoss)).append("</col>");
        if (detail != null && !detail.isEmpty())
        {
            msg.append(" (").append(escapeCol(detail)).append(")");
        }
        notifyGame(msg.toString());
    }

    private void notifyGame(String message)
    {
        if (message == null || message.isEmpty())
        {
            return;
        }
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage("<col=a6480c>[Ironforged Events]</col> " + message)
            .build());
    }

    private static String escapeCol(String value)
    {
        // Strip tags so player/item names cannot break col formatting.
        return value.replace("<", "").replace(">", "");
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private synchronized boolean rememberDedupe(String key)
    {
        if (recentDedupe.contains(key))
        {
            return false;
        }
        recentDedupe.add(key);
        while (recentDedupe.size() > MAX_RECENT_DEDUPE)
        {
            String first = recentDedupe.iterator().next();
            recentDedupe.remove(first);
        }
        return true;
    }

    private String resolveItemName(int itemId)
    {
        try
        {
            ItemComposition composition = itemManager.getItemComposition(itemId);
            if (composition == null || composition.getName() == null)
            {
                return "";
            }
            return composition.getName().replace(" (noted)", "").replace(" (broken)", "").trim();
        }
        catch (Exception e)
        {
            log.debug("Item name resolve failed for {}: {}", itemId, e.getMessage());
            return "";
        }
    }

    private String localRsn()
    {
        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null)
        {
            return "";
        }
        return local.getName().replace('\u00A0', ' ').trim();
    }

    private String localClanName()
    {
        try
        {
            ClanChannel channel = client.getClanChannel();
            if (channel != null && channel.getName() != null)
            {
                return channel.getName().replace('\u00A0', ' ').trim();
            }
        }
        catch (Exception e)
        {
            log.debug("Clan name resolve failed: {}", e.getMessage());
        }
        return "";
    }

    private static BufferedImage copyImage(Image image)
    {
        if (image == null)
        {
            return null;
        }
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0)
        {
            return null;
        }
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try
        {
            g.drawImage(image, 0, 0, null);
        }
        finally
        {
            g.dispose();
        }
        return copy;
    }

    private static BufferedImage scaleForUpload(BufferedImage source)
    {
        if (source == null)
        {
            return null;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_SCREENSHOT_WIDTH)
        {
            return source;
        }
        int scaledHeight = Math.max(1, (int) Math.round(height * (MAX_SCREENSHOT_WIDTH / (double) width)));
        BufferedImage scaled = new BufferedImage(MAX_SCREENSHOT_WIDTH, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, MAX_SCREENSHOT_WIDTH, scaledHeight, null);
        }
        finally
        {
            g.dispose();
        }
        return scaled;
    }

    private static byte[] encodeJpeg(BufferedImage image)
    {
        if (image == null)
        {
            return null;
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext())
        {
            writers = ImageIO.getImageWritersByFormatName("jpeg");
        }
        if (!writers.hasNext())
        {
            return null;
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out))
        {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed())
            {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            return out.toByteArray();
        }
        catch (Exception e)
        {
            writer.dispose();
            return null;
        }
    }

    private static BufferedImage tinyImage()
    {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xE88C24);
        img.setRGB(1, 0, 0xE88C24);
        img.setRGB(0, 1, 0x373737);
        img.setRGB(1, 1, 0x373737);
        return img;
    }

    private static String truncate(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    private static final class PendingDrop
    {
        final SeasonalCatalogEntry entry;
        final int itemId;
        final int quantity;

        PendingDrop(SeasonalCatalogEntry entry, int itemId, int quantity)
        {
            this.entry = entry;
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    private static final class UploadResult
    {
        final boolean success;
        final boolean retryable;
        final String message;

        UploadResult(boolean success, boolean retryable, String message)
        {
            this.success = success;
            this.retryable = retryable;
            this.message = message == null ? "" : message;
        }

        static UploadResult ok()
        {
            return new UploadResult(true, false, "");
        }

        static UploadResult fatal(String message)
        {
            return new UploadResult(false, false, message);
        }
    }
}
