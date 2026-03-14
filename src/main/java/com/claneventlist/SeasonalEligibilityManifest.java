package com.claneventlist;

import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Data
public class SeasonalEligibilityManifest
{
    private Set<String> eligibleBossKeys = new HashSet<>();
    private Set<Integer> eligibleItemIds = new HashSet<>();
    private long loadedAtEpochMs;

    public boolean hasBossFilter()
    {
        return !eligibleBossKeys.isEmpty();
    }

    public boolean hasItemFilter()
    {
        return !eligibleItemIds.isEmpty();
    }

    public Set<String> readonlyBossKeys()
    {
        return Collections.unmodifiableSet(eligibleBossKeys);
    }

    public Set<Integer> readonlyItemIds()
    {
        return Collections.unmodifiableSet(eligibleItemIds);
    }
}

