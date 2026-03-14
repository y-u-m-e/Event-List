package com.claneventlist;

import lombok.Data;

@Data
public class SeasonalTelemetry
{
    private int queuedCount;
    private int sentCount;
    private int failedCount;
    private String lastApiResponse = "Not sent yet";
    private String lastError = "";
    private boolean pausedForAuth;
    private String linkStatus = "Not linked";
    private String manifestStatus = "Manifest not loaded";
}

