package com.claneventlist;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeasonalApiResult
{
    private boolean success;
    private boolean retryable;
    private boolean duplicateHandled;
    private boolean authFailure;
    private boolean identityMismatch;
    private int httpCode;
    private String message;
}

