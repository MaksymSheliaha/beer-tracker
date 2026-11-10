package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ConsumptionAnalyticsResponse(
        @JsonProperty("timezone") String timezone,
        @JsonProperty("current_streak") int currentStreak,
        @JsonProperty("longest_streak") int longestStreak,
        @JsonProperty("total_volume_ml") int totalVolumeMl,
        @JsonProperty("total_entries") int totalEntries,
        @JsonProperty("hourly_profile") List<HourlyProfileItem> hourlyProfile
) {
}
