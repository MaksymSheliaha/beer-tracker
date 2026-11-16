package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Aggregated consumption analytics response computed in the user's IANA timezone,
 * including streak metrics and a 24-slot hourly profile ({@code REQ-F-012}).
 *
 * @param timezone      user's resolved IANA timezone identifier
 * @param currentStreak current consecutive consumption day streak
 * @param longestStreak longest historical consecutive consumption day streak
 * @param totalVolumeMl total consumed volume in milliliters across the queried period
 * @param totalEntries  total consumption log entries across the queried period
 * @param hourlyProfile 24-element hourly breakdown for slots {@code 0..23}
 */
public record ConsumptionAnalyticsResponse(
        @JsonProperty("timezone") String timezone,
        @JsonProperty("current_streak") int currentStreak,
        @JsonProperty("longest_streak") int longestStreak,
        @JsonProperty("total_volume_ml") int totalVolumeMl,
        @JsonProperty("total_entries") int totalEntries,
        @JsonProperty("hourly_profile") List<HourlyProfileItem> hourlyProfile
) {
}
