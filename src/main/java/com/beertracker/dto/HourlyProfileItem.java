package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single hourly slot ({@code 0..23}) in {@link ConsumptionAnalyticsResponse} ({@code AC-012.3},
 * {@code AC-012.4}).
 *
 * @param hour            hour of day in user's local IANA timezone ({@code 0..23})
 * @param volumeMl        total consumed volume in milliliters during this hour
 * @param entryCount      number of consumption entries recorded during this hour
 * @param averageVolumeMl average volume per active day ({@code volumeMl / totalActiveDays})
 */
public record HourlyProfileItem(
        @JsonProperty("hour") int hour,
        @JsonProperty("volume_ml") int volumeMl,
        @JsonProperty("entry_count") int entryCount,
        @JsonProperty("average_volume_ml") double averageVolumeMl
) {
}
