package com.claneventlist;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonalQueueItem
{
    private SeasonalSubmission submission;
    private int attempts;
    private long nextAttemptEpochMs;
    private String lastError;
}

