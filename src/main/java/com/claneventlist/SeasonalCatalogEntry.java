package com.claneventlist;

/**
 * One notable drop from {@code GET /plugins/seasonal} catalog.
 */
final class SeasonalCatalogEntry
{
    private final String boss;
    private final String item;
    private final int itemId;
    private final String bossKey;
    private final String itemKey;

    SeasonalCatalogEntry(String boss, String item, int itemId)
    {
        this.boss = boss == null ? "" : boss.trim();
        this.item = item == null ? "" : item.trim();
        this.itemId = itemId > 0 ? itemId : 0;
        this.bossKey = normalizeKey(this.boss);
        this.itemKey = normalizeKey(this.item);
    }

    String getBoss()
    {
        return boss;
    }

    String getItem()
    {
        return item;
    }

    /**
     * OSRS item id from the API when published; {@code 0} if unknown.
     */
    int getItemId()
    {
        return itemId;
    }

    /**
     * Prefer item-id match when both sides have an id; otherwise fall back to normalized names.
     */
    boolean matches(String bossName, String itemName, int lootItemId)
    {
        if (!bossKey.equals(normalizeKey(bossName)))
        {
            return false;
        }
        if (itemId > 0 && lootItemId > 0)
        {
            return itemId == lootItemId;
        }
        return itemKey.equals(normalizeKey(itemName));
    }

    /**
     * Matches the API {@code normalizeCatalogKey} used by yume-api.
     */
    static String normalizeKey(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value
            .trim()
            .toLowerCase()
            .replace('_', ' ')
            .replaceAll("\\s+", " ")
            .replaceAll("[^a-z0-9 ]", "")
            .trim();
    }
}
