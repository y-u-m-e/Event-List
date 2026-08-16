package com.claneventlist;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Slot placement for GET /plugins/seasonal/teams. Badge color and label follow the
 * slot, so putting a team in the wrong one is silently visible in-game.
 */
public class EventTeamSyncServiceTest
{
    private static EventTeamSyncService.SyncedTeam team(String name, int slot)
    {
        EventTeamSyncService.SyncedTeam t = new EventTeamSyncService.SyncedTeam();
        t.name = name;
        t.slot = slot;
        return t;
    }

    @Test
    public void keepsSparseSlotsWhereTheApiPutThem()
    {
        Map<Integer, EventTeamSyncService.SyncedTeam> bySlot = EventTeamSyncService.mapBySlot(
            Arrays.asList(team("Copper", 1), team("Steel", 5)));

        assertEquals(2, bySlot.size());
        assertEquals("Copper", bySlot.get(1).name);
        assertEquals("Steel", bySlot.get(5).name);
        // The gap must stay empty rather than sliding Steel down to slot 2.
        assertTrue(bySlot.get(2) == null);
    }

    @Test
    public void honoursSlotRatherThanPayloadOrder()
    {
        Map<Integer, EventTeamSyncService.SyncedTeam> bySlot = EventTeamSyncService.mapBySlot(
            Arrays.asList(team("Third", 3), team("First", 1)));

        assertEquals("First", bySlot.get(1).name);
        assertEquals("Third", bySlot.get(3).name);
    }

    @Test
    public void firstTeamWinsADuplicateSlot()
    {
        Map<Integer, EventTeamSyncService.SyncedTeam> bySlot = EventTeamSyncService.mapBySlot(
            Arrays.asList(team("Winner", 2), team("Loser", 2)));

        assertEquals(1, bySlot.size());
        assertEquals("Winner", bySlot.get(2).name);
    }

    @Test
    public void dropsSlotsOutsideTheSupportedRange()
    {
        Map<Integer, EventTeamSyncService.SyncedTeam> bySlot = EventTeamSyncService.mapBySlot(
            Arrays.asList(
                team("TooLow", 0),
                team("Ok", 1),
                team("TooHigh", EventTeamService.MAX_TEAMS + 1)));

        assertEquals(1, bySlot.size());
        assertEquals("Ok", bySlot.get(1).name);
    }

    @Test
    public void arrayOrderFallbackStillFillsSlotsOneUp()
    {
        // What parseTeam produces when the API omits "slot" entirely.
        List<EventTeamSyncService.SyncedTeam> teams = new ArrayList<>();
        for (int i = 1; i <= 3; i++)
        {
            teams.add(team("Team" + i, i));
        }

        Map<Integer, EventTeamSyncService.SyncedTeam> bySlot = EventTeamSyncService.mapBySlot(teams);

        assertEquals(3, bySlot.size());
        assertEquals("Team1", bySlot.get(1).name);
        assertEquals("Team2", bySlot.get(2).name);
        assertEquals("Team3", bySlot.get(3).name);
    }
}
