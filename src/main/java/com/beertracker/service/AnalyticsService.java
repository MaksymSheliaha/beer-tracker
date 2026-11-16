package com.beertracker.service;

import com.beertracker.dto.ConsumptionAnalyticsResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.HourlyProfileItem;
import com.beertracker.dto.StreakResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.model.ConsumptionEntry;
import com.beertracker.model.User;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service computing user consumption time-series analytics, 24-hour profiles,
 * and consumption day streaks anchored to each user's IANA timezone ({@code REQ-F-012}).
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsService.class);

    private final ConsumptionRepository consumptionRepository;
    private final UserRepository userRepository;
    private final StreakCalculator streakCalculator;

    /**
     * Constructs the analytics service.
     *
     * @param consumptionRepository repository supplying user consumption logs
     * @param userRepository        repository supplying user IANA timezones
     * @param streakCalculator      calculator determining consecutive consumption day streaks
     */
    public AnalyticsService(
            ConsumptionRepository consumptionRepository,
            UserRepository userRepository,
            StreakCalculator streakCalculator
    ) {
        this.consumptionRepository = consumptionRepository;
        this.userRepository = userRepository;
        this.streakCalculator = streakCalculator;
    }

    /**
     * Calculates current and longest consumption streaks for the specified user in their
     * configured IANA timezone, using the half-open {@code [00:00:00.000, 24:00:00.000)}
     * day boundary ({@code AC-012.1}, {@code AC-012.2}).
     *
     * @param userId authenticated user UUID
     * @return {@link StreakResponse} containing current/longest streak counts and active status
     */
    public StreakResponse getStreaks(UUID userId) {
        long startNs = System.nanoTime();
        ZoneId zoneId = resolveUserZoneId(userId);
        List<ConsumptionEntry> userEntries = consumptionRepository.findByUserId(userId);

        StreakResponse response = streakCalculator.calculateStreaks(userEntries, zoneId, Instant.now());
        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;

        LOG.info("Streaks calculated for user",
                kv("event", "streaks_calculated"),
                kv("user_id", userId.toString()),
                kv("timezone", zoneId.getId()),
                kv("current_streak", response.currentStreak()),
                kv("longest_streak", response.longestStreak()),
                kv("duration_ms", durationMs),
                kv("status", "200"));

        return response;
    }

    /**
     * Computes aggregated consumption volume, entry count, streaks, and a 24-slot hourly
     * profile for an optional target date or date range ({@code AC-012.3}, {@code AC-012.4}).
     *
     * @param userId      authenticated user UUID
     * @param dateStr     optional exact local date filter ({@code YYYY-MM-DD})
     * @param dateFromStr optional inclusive start date filter ({@code YYYY-MM-DD})
     * @param dateToStr   optional inclusive end date filter ({@code YYYY-MM-DD})
     * @return {@link ConsumptionAnalyticsResponse} with 24 hourly slots
     * @throws ApiException with HTTP {@code 400 BAD_REQUEST} when any date string fails ISO-8601 parsing
     */
    public ConsumptionAnalyticsResponse getConsumptionAnalytics(
            UUID userId,
            String dateStr,
            String dateFromStr,
            String dateToStr
    ) {
        long startNs = System.nanoTime();
        ZoneId zoneId = resolveUserZoneId(userId);

        LocalDate exactDate = parseOptionalDate(dateStr, "date");
        LocalDate dateFrom = parseOptionalDate(dateFromStr, "date_from");
        LocalDate dateTo = parseOptionalDate(dateToStr, "date_to");

        List<ConsumptionEntry> allUserEntries = consumptionRepository.findByUserId(userId);
        StreakResponse streaks = streakCalculator.calculateStreaks(allUserEntries, zoneId, Instant.now());

        int[] volumeByHour = new int[24];
        int[] countByHour = new int[24];
        Set<LocalDate> activeDaysInPeriod = new HashSet<>();
        int totalVolumeMl = 0;
        int totalEntries = 0;

        for (ConsumptionEntry entry : allUserEntries) {
            if (entry.consumedAt() == null) {
                continue;
            }
            ZonedDateTime zdt = entry.consumedAt().atZone(zoneId);
            LocalDate localDate = zdt.toLocalDate();

            if (exactDate != null && !localDate.equals(exactDate)) {
                continue;
            }
            if (dateFrom != null && localDate.isBefore(dateFrom)) {
                continue;
            }
            if (dateTo != null && localDate.isAfter(dateTo)) {
                continue;
            }

            int hour = zdt.getHour();
            int vol = entry.totalVolumeMl() != null ? entry.totalVolumeMl() : 0;
            volumeByHour[hour] += vol;
            countByHour[hour] += 1;
            activeDaysInPeriod.add(localDate);
            totalVolumeMl += vol;
            totalEntries += 1;
        }

        int totalActiveDays = activeDaysInPeriod.size();
        List<HourlyProfileItem> hourlyProfile = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            double avgVolume = totalActiveDays == 0
                    ? 0.0
                    : Math.round(((double) volumeByHour[h] / totalActiveDays) * 100.0) / 100.0;
            hourlyProfile.add(new HourlyProfileItem(
                    h,
                    volumeByHour[h],
                    countByHour[h],
                    avgVolume
            ));
        }

        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;
        LOG.info("Consumption analytics calculated",
                kv("event", "analytics_calculated"),
                kv("user_id", userId.toString()),
                kv("timezone", zoneId.getId()),
                kv("total_volume_ml", totalVolumeMl),
                kv("total_entries", totalEntries),
                kv("active_days_count", totalActiveDays),
                kv("duration_ms", durationMs),
                kv("status", "200"));

        return new ConsumptionAnalyticsResponse(
                zoneId.getId(),
                streaks.currentStreak(),
                streaks.longestStreak(),
                totalVolumeMl,
                totalEntries,
                hourlyProfile
        );
    }

    private ZoneId resolveUserZoneId(UUID userId) {
        String tz = userRepository.findById(userId)
                .map(User::timezone)
                .orElse("UTC");
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    private LocalDate parseOptionalDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Invalid date format for parameter '" + fieldName + "'",
                    List.of(new ErrorDetail(fieldName, "Date must be in ISO-8601 format YYYY-MM-DD"))
            );
        }
    }
}
