package com.claneventlist;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonalIdentity
{
    private String eventId;
    private String eventPassphrase;
    private String pluginKeyword;
    private String clientInstanceId;

    public boolean isLinked()
    {
        return nonEmpty(eventId) && nonEmpty(eventPassphrase) && nonEmpty(pluginKeyword);
    }

    private boolean nonEmpty(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}

