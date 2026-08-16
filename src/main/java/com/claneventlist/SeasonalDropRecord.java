package com.claneventlist;

/**
 * One submitted (or attempted) seasonal drop, shown in the Seasonal tab history.
 */
final class SeasonalDropRecord
{
    String id = "";
    String status = "submitted"; // submitted | failed | queued
    String rsn = "";
    String clan = "";
    String boss = "";
    int itemId;
    String itemName = "";
    int quantity = 1;
    String eventId = "";
    String date = "";
    String time = "";
    String droppedAt = "";
    String detail = "";
    long savedAtEpochMs;

    String summaryLine()
    {
        String item = itemName == null || itemName.isEmpty() ? ("#" + itemId) : itemName;
        String when = (date == null ? "" : date) + (time == null || time.isEmpty() ? "" : " " + time);
        return (when.isEmpty() ? "" : when + " · ") + boss + " — " + item;
    }
}
