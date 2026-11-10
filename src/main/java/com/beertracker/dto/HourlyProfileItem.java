package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HourlyProfileItem(
        @JsonProperty("hour") int hour,
        @JsonProperty("volume_ml") int volumeMl,
        @JsonProperty("entry_count") int entryCount,
        @JsonProperty("average_volume_ml") double averageVolumeMl
) {
}
