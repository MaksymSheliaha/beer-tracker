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

/**
 * Pure domain calculator determining consecutive consumption day streaks in a user's
 * IANA timezone using the {@code [00:00:00.000, 24:00:00.000)} {@code Day Boundary}
 * ({@code REQ-F-012}, {@code AC-012.1}, {@code AC-012.2}).
 */
@Component
public class StreakCalculator {

    /**
     * Computes {@code current_streak}, {@code longest_streak}, {@code last_consumed_date},
     * and {@code is_active_today} relative to {@code referenceNow} in {@code zoneId}.
     *
     * @param entries      user's consumption entries
     * @param zoneId       user's IANA timezone
     * @param referenceNow current reference instant (typically {@link Instant#now()})
     * @return calculated {@link StreakResponse}
     */
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

        boolean isActiveToday = activeDates.contains(today);
        LocalDate yesterday = today.minusDays(1);
        LocalDate anchorDay = isActiveToday ? today : (activeDates.contains(yesterday) ? yesterday : null);

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
