package com.claneventlist;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class SeasonalReporterServiceTest
{
    @Test
    public void testPayloadSerializationUsesExpectedFieldNames()
    {
        SeasonalSubmission submission = new SeasonalSubmission(
            "seasonal-2026-q2",
            "zeber_is_stinky",
            null,
            SeasonalReporterService.PLUGIN_KEYWORD,
            "client-uuid-1",
            "cerberus",
            13229,
            1,
            "2026-04-08T21:17:31.000Z",
            "plugin",
            "Yume",
            "runelite:123:345:678",
            null
        );

        String json = new Gson().toJson(submission);
        Assert.assertTrue(json.contains("\"event_id\":\"seasonal-2026-q2\""));
        Assert.assertTrue(json.contains("\"event_passphrase\":\"zeber_is_stinky\""));
        Assert.assertTrue(json.contains("\"plugin_keyword\":\"if_event_list_rl_v1\""));
        Assert.assertTrue(json.contains("\"client_instance_id\":\"client-uuid-1\""));
        Assert.assertTrue(json.contains("\"boss_key\":\"cerberus\""));
        Assert.assertTrue(json.contains("\"player_rsn\":\"Yume\""));
        Assert.assertTrue(json.contains("\"source_ref\":\"runelite:123:345:678\""));
    }

    @Test
    public void testQueueRetryBackoffGrows()
    {
        long first = SeasonalSubmissionQueue.backoffMillis(1);
        long second = SeasonalSubmissionQueue.backoffMillis(2);
        long tenth = SeasonalSubmissionQueue.backoffMillis(10);

        Assert.assertTrue(second > first);
        Assert.assertTrue(tenth >= second);
    }

    @Test
    public void testDedupeKeyGenerationBucketsByHour()
    {
        String ts1 = Instant.parse("2026-04-08T21:17:31.000Z").toString();
        String ts2 = Instant.parse("2026-04-08T21:59:59.000Z").toString();
        String ts3 = Instant.parse("2026-04-08T22:00:00.000Z").toString();

        String key1 = SeasonalSubmissionQueue.dedupeKey("event1", "passphraseA", "clientX", "cerberus", 13229, ts1);
        String key2 = SeasonalSubmissionQueue.dedupeKey("event1", "passphraseA", "clientX", "cerberus", 13229, ts2);
        String key3 = SeasonalSubmissionQueue.dedupeKey("event1", "passphraseA", "clientX", "cerberus", 13229, ts3);

        Assert.assertEquals(key1, key2);
        Assert.assertNotEquals(key1, key3);
    }

    @Test
    public void testTamperGuardIdentityComesFromLinkedIdentity()
    {
        SeasonalIdentity identity = new SeasonalIdentity("event_server", "passphrase_server", SeasonalReporterService.PLUGIN_KEYWORD, "client-uuid");
        SeasonalSubmission submission = SeasonalReporterService.buildSubmission(
            identity,
            "abyssal_sire",
            123,
            2,
            "2026-04-08T21:17:31.000Z",
            "Yume",
            "runelite:source"
        );

        Assert.assertEquals("event_server", submission.getEventId());
        Assert.assertEquals("passphrase_server", submission.getEventPassphrase());
        Assert.assertEquals("if_event_list_rl_v1", submission.getPluginKeyword());
        Assert.assertEquals("client-uuid", submission.getClientInstanceId());
        Assert.assertEquals("Yume", submission.getPlayerRsn());
        Assert.assertEquals("plugin", submission.getSource());
    }
}

