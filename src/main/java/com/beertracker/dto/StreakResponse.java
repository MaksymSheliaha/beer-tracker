package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User consumption streak summary computed in the user's IANA timezone ({@code REQ-F-012}).
 *
 * @param currentStreak    consecutive days with &gt;= 1 entry ending today or yesterday
 * @param longestStreak    longest historical consecutive day streak
 * @param lastConsumedDate ISO local date ({@code YYYY-MM-DD}) of the latest recorded consumption
 * @param isActiveToday    whether at least one consumption entry is recorded for local today
 */
public record StreakResponse(
        @JsonProperty("current_streak") int currentStreak,
        @JsonProperty("longest_streak") int longestStreak,
        @JsonProperty("last_consumed_date") String lastConsumedDate,
        @JsonProperty("is_active_today") boolean isActiveToday
) {
}
