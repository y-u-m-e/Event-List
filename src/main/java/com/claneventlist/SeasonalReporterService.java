package com.claneventlist;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Singleton
public class SeasonalReporterService
{
    static final String API_SOURCE = "plugin";
    static final String PLUGIN_KEYWORD = "if_event_list_rl_v1";
    private static final String KEY_CLIENT_INSTANCE_ID = "seasonalClientInstanceId";

    private final EventListConfig config;
    private final ConfigManager configManager;
    private final Client client;
    private final SeasonalApiClient apiClient;
    private final SeasonalSubmissionQueue queue;
    private final SeasonalTelemetry telemetry = new SeasonalTelemetry();
    private SeasonalEligibilityManifest manifest = new SeasonalEligibilityManifest();

    private long tickCounter = 0L;

    @Inject
    public SeasonalReporterService(
        EventListConfig config,
        ConfigManager configManager,
        Client client,
        SeasonalApiClient apiClient,
        SeasonalSubmissionQueue queue
    )
    {
        this.config = config;
        this.configManager = configManager;
        this.client = client;
        this.apiClient = apiClient;
        this.queue = queue;
    }

    public void start()
    {
        queue.loadFromDisk();
        refreshLinkStatus();
        telemetry.setQueuedCount(queue.queueSize());
        refreshManifest();
    }

    public void stop()
    {
        queue.saveToDisk();
    }

    public void onGameTick()
    {
        tickCounter++;
    }

    public void onNpcLootReceived(NpcLootReceived event)
    {
        if (!config.seasonalEnabled())
        {
            return;
        }

        SeasonalIdentity identity = getIdentity();
        if (!identity.isLinked())
        {
            telemetry.setLastError("Seasonal plugin not linked (connect code required).");
            refreshLinkStatus();
            return;
        }

        String bossName = event.getNpc() != null ? event.getNpc().getName() : "";
        String bossKey = normalizeBossKey(bossName);
        if (bossKey.isEmpty())
        {
            return;
        }

        for (ItemStack item : event.getItems())
        {
            if (!isLocallyEligible(bossKey, item.getId()))
            {
                continue;
            }

            SeasonalSubmission submission = buildSubmission(
                identity,
                bossKey,
                item.getId(),
                item.getQuantity(),
                Instant.now().toString(),
                buildSourceRef(client.getAccountHash(), client.getWorld(), tickCounter, item.getId(), item.getQuantity())
            );

            if (config.seasonalDryRun())
            {
                telemetry.setLastApiResponse("Dry run captured: " + bossKey + " item=" + item.getId() + " x" + item.getQuantity());
                continue;
            }

            boolean accepted = queue.enqueueIfNotDuplicate(submission);
            if (!accepted)
            {
                telemetry.setLastApiResponse("Duplicate filtered locally");
            }
        }

        telemetry.setQueuedCount(queue.queueSize());
        queue.saveToDisk();
    }

    public void processQueue()
    {
        if (!config.seasonalEnabled() || config.seasonalDryRun())
        {
            telemetry.setQueuedCount(queue.queueSize());
            return;
        }

        if (telemetry.isPausedForAuth())
        {
            return;
        }

        SeasonalIdentity identity = getIdentity();
        if (!identity.isLinked())
        {
            telemetry.setLastError("Seasonal not linked.");
            refreshLinkStatus();
            return;
        }

        List<SeasonalQueueItem> ready = queue.pollReady(20);
        if (ready.isEmpty())
        {
            telemetry.setQueuedCount(queue.queueSize());
            return;
        }

        List<SeasonalSubmission> payload = new ArrayList<>();
        for (SeasonalQueueItem item : ready)
        {
            payload.add(item.getSubmission());
        }

        SeasonalApiResult bulkResult = apiClient.submitBulk(identity, payload);
        if ("bulk_unsupported".equals(bulkResult.getMessage()))
        {
            handleSingles(identity, ready);
            queue.saveToDisk();
            telemetry.setQueuedCount(queue.queueSize());
            return;
        }

        if (bulkResult.isSuccess())
        {
            telemetry.setSentCount(telemetry.getSentCount() + ready.size());
            telemetry.setLastApiResponse("Bulk sent " + ready.size() + " item(s)");
        }
        else if (bulkResult.isDuplicateHandled())
        {
            telemetry.setSentCount(telemetry.getSentCount() + ready.size());
            telemetry.setLastApiResponse("Bulk duplicate_window handled");
        }
        else if (bulkResult.isAuthFailure())
        {
            telemetry.setPausedForAuth(true);
            queue.requeueFront(ready, "auth_failure");
            telemetry.setLastError("Auth expired. Re-link required.");
            telemetry.setLastApiResponse("HTTP " + bulkResult.getHttpCode() + " auth failure");
        }
        else if (bulkResult.isRetryable())
        {
            queue.requeueFront(ready, bulkResult.getMessage());
            telemetry.setLastError("Retrying bulk: " + bulkResult.getMessage());
            telemetry.setLastApiResponse("Bulk retry scheduled");
        }
        else
        {
            for (SeasonalQueueItem item : ready)
            {
                queue.markDeadLetter(item, bulkResult.getMessage());
            }
            telemetry.setFailedCount(telemetry.getFailedCount() + ready.size());
            telemetry.setLastError("Bulk failed: " + bulkResult.getMessage());
            telemetry.setLastApiResponse("Bulk dead-lettered");
        }

        queue.saveToDisk();
        telemetry.setQueuedCount(queue.queueSize());
    }

    public boolean linkWithConnectCode(String connectCode)
    {
        if (config.seasonalEventId() == null || config.seasonalEventId().trim().isEmpty())
        {
            telemetry.setLastError("Seasonal Event ID is required.");
            return false;
        }
        if (connectCode == null || connectCode.trim().isEmpty())
        {
            telemetry.setLastError("Ingest key is required.");
            return false;
        }
        telemetry.setPausedForAuth(false);
        telemetry.setLastApiResponse("Settings validated");
        telemetry.setLastError("");
        refreshLinkStatus();
        return true;
    }

    public SeasonalApiResult testApi()
    {
        SeasonalIdentity identity = getIdentity();
        if (!identity.isLinked())
        {
            SeasonalApiResult result = new SeasonalApiResult(false, false, false, false, false, 0, "not_linked");
            telemetry.setLastError("Not linked");
            return result;
        }

        SeasonalApiResult result = apiClient.checkState(identity);
        telemetry.setLastApiResponse("State check HTTP " + result.getHttpCode() + " " + result.getMessage());
        if (result.isAuthFailure())
        {
            telemetry.setPausedForAuth(true);
            telemetry.setLastError("Auth expired. Re-link required.");
        }
        return result;
    }

    public void flushQueueNow()
    {
        processQueue();
    }

    public void enqueueDebugDrop()
    {
        SeasonalIdentity identity = getIdentity();
        if (!identity.isLinked())
        {
            telemetry.setLastError("Cannot enqueue debug drop: not linked.");
            return;
        }

        SeasonalSubmission submission = buildSubmission(
            identity,
            "debug_boss",
            995,
            1,
            Instant.now().toString(),
            "runelite:debug:" + System.currentTimeMillis()
        );

        if (config.seasonalDryRun())
        {
            telemetry.setLastApiResponse("Dry run debug drop generated");
            return;
        }

        queue.enqueueIfNotDuplicate(submission);
        queue.saveToDisk();
        telemetry.setQueuedCount(queue.queueSize());
        telemetry.setLastApiResponse("Debug drop queued");
    }

    public void refreshManifest()
    {
        SeasonalIdentity identity = getIdentity();
        if (!identity.isLinked())
        {
            manifest = new SeasonalEligibilityManifest();
            telemetry.setManifestStatus("Manifest unavailable (not linked)");
            return;
        }

        com.google.gson.JsonObject state = apiClient.fetchStateJson(identity);
        SeasonalEligibilityManifest parsed = apiClient.parseEligibilityManifest(state);
        manifest = parsed;

        Set<String> bosses = parsed.readonlyBossKeys();
        Set<Integer> items = parsed.readonlyItemIds();
        if (bosses.isEmpty() && items.isEmpty())
        {
            telemetry.setManifestStatus("Manifest loaded (server fallback-only)");
        }
        else
        {
            telemetry.setManifestStatus("Manifest loaded: bosses=" + bosses.size() + ", items=" + items.size());
        }
    }

    public SeasonalTelemetry getTelemetry()
    {
        telemetry.setQueuedCount(queue.queueSize());
        refreshLinkStatus();
        return telemetry;
    }

    static SeasonalSubmission buildSubmission(
        SeasonalIdentity identity,
        String bossKey,
        int itemId,
        int quantity,
        String droppedAtIso,
        String sourceRef
    )
    {
        SeasonalSubmission submission = new SeasonalSubmission();
        submission.setEventId(identity.getEventId());
        submission.setIngestId(identity.getIngestId());
        submission.setPluginKeyword(identity.getPluginKeyword());
        submission.setClientInstanceId(identity.getClientInstanceId());
        submission.setBossKey(bossKey);
        submission.setItemId(itemId);
        submission.setQuantity(quantity);
        submission.setDroppedAt(droppedAtIso);
        submission.setSource(API_SOURCE);
        submission.setSourceRef(sourceRef);
        return submission;
    }

    static String buildSourceRef(long accountHash, int world, long tick, int itemId, int quantity)
    {
        return "runelite:" + accountHash + ":" + world + ":" + tick + ":" + itemId + ":" + quantity;
    }

    static String normalizeBossKey(String npcName)
    {
        if (npcName == null)
        {
            return "";
        }
        String cleaned = npcName.trim().toLowerCase(Locale.US)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");
        return cleaned;
    }

    private void handleSingles(SeasonalIdentity identity, List<SeasonalQueueItem> items)
    {
        for (SeasonalQueueItem item : items)
        {
            SeasonalApiResult result = apiClient.submitSingle(identity, item.getSubmission());
            if (result.isSuccess() || result.isDuplicateHandled())
            {
                telemetry.setSentCount(telemetry.getSentCount() + 1);
            }
            else if (result.isAuthFailure())
            {
                telemetry.setPausedForAuth(true);
                queue.markRetry(item, "auth_failure");
                telemetry.setLastError("Auth expired. Re-link required.");
            }
            else if (result.isRetryable())
            {
                queue.markRetry(item, result.getMessage());
            }
            else
            {
                queue.markDeadLetter(item, result.getMessage());
                telemetry.setFailedCount(telemetry.getFailedCount() + 1);
            }
        }
        telemetry.setLastApiResponse("Single endpoint processed " + items.size() + " item(s)");
    }

    private SeasonalIdentity getIdentity()
    {
        SeasonalIdentity identity = new SeasonalIdentity();
        identity.setEventId(config.seasonalEventId() != null ? config.seasonalEventId().trim() : "");
        identity.setIngestId(config.seasonalConnectCode() != null ? config.seasonalConnectCode().trim() : "");
        identity.setPluginKeyword(PLUGIN_KEYWORD);
        String existingClientId = configManager.getConfiguration(EventListConfig.CONFIG_GROUP, KEY_CLIENT_INSTANCE_ID);
        if (existingClientId == null || existingClientId.trim().isEmpty())
        {
            existingClientId = java.util.UUID.randomUUID().toString();
            configManager.setConfiguration(EventListConfig.CONFIG_GROUP, KEY_CLIENT_INSTANCE_ID, existingClientId);
        }
        identity.setClientInstanceId(existingClientId);
        return identity;
    }

    private void refreshLinkStatus()
    {
        SeasonalIdentity identity = getIdentity();
        if (!identity.isLinked())
        {
            telemetry.setLinkStatus("Not configured");
            return;
        }
        telemetry.setLinkStatus("Configured: " + identity.getEventId() + " / " + identity.getIngestId());
    }

    private boolean isLocallyEligible(String bossKey, int itemId)
    {
        Set<String> bosses = manifest != null ? manifest.readonlyBossKeys() : new HashSet<>();
        Set<Integer> items = manifest != null ? manifest.readonlyItemIds() : new HashSet<>();
        if (!bosses.isEmpty() && !bosses.contains(bossKey))
        {
            return false;
        }
        if (!items.isEmpty() && !items.contains(itemId))
        {
            return false;
        }
        return true;
    }
}

