package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StreakResponse(
        @JsonProperty("current_streak") int currentStreak,
        @JsonProperty("longest_streak") int longestStreak,
        @JsonProperty("last_consumed_date") String lastConsumedDate,
        @JsonProperty("is_active_today") boolean isActiveToday
) {
}
