package com.beertracker.service;

import com.beertracker.dto.StreakResponse;
import com.beertracker.model.ConsumptionEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

@Component
public class StreakCalculator {

    public StreakResponse calculateStreaks(List<ConsumptionEntry> entries, ZoneId zoneId, Instant referenceNow) {
        LocalDate today = referenceNow.atZone(zoneId).toLocalDate();

        if (entries == null || entries.isEmpty()) {
            return new StreakResponse(0, 0, null, false);
        }

        NavigableSet<LocalDate> activeDates = new TreeSet<>();
        for (ConsumptionEntry entry : entries) {
            if (entry.consumedAt() != null) {
                LocalDate localDay = entry.consumedAt().atZone(zoneId).toLocalDate();
                activeDates.add(localDay);
            }
        }

        if (activeDates.isEmpty()) {
            return new StreakResponse(0, 0, null, false);
        }

        // Longest streak calculation
        int longestStreak = 0;
        int runningStreak = 0;
        LocalDate previousDay = null;
        for (LocalDate day : activeDates) {
            if (previousDay != null && day.equals(previousDay.plusDays(1))) {
                runningStreak++;
            } else {
                runningStreak = 1;
            }
            if (runningStreak > longestStreak) {
                longestStreak = runningStreak;
            }
            previousDay = day;
        }

        // Current streak calculation (must end on today or yesterday in user's timezone)
        boolean isActiveToday = activeDates.contains(today);
        LocalDate anchorDay = isActiveToday ? today : (activeDates.contains(today.minusDays(1)) ? today.minusDays(1) : null);

        int currentStreak = 0;
        if (anchorDay != null) {
            LocalDate cursor = anchorDay;
            while (activeDates.contains(cursor)) {
                currentStreak++;
                cursor = cursor.minusDays(1);
            }
        }

        String lastConsumedDate = activeDates.last().toString();
        return new StreakResponse(currentStreak, longestStreak, lastConsumedDate, isActiveToday);
    }
}
