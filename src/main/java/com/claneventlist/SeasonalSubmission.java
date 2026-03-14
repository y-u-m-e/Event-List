package com.claneventlist;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonalSubmission
{
    @SerializedName("event_id")
    private String eventId;

    @SerializedName("ingest_id")
    private String ingestId;

    @SerializedName("plugin_keyword")
    private String pluginKeyword;

    @SerializedName("client_instance_id")
    private String clientInstanceId;

    @SerializedName("boss_key")
    private String bossKey;

    @SerializedName("item_id")
    private int itemId;

    private int quantity;

    @SerializedName("dropped_at")
    private String droppedAt;

    private String source;

    @SerializedName("source_ref")
    private String sourceRef;

    @SerializedName("screenshot_base64")
    private String screenshotBase64;
}

