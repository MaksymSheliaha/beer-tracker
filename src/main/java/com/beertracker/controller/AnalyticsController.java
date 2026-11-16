package com.beertracker.controller;

import com.beertracker.dto.ConsumptionAnalyticsResponse;
import com.beertracker.dto.StreakResponse;
import com.beertracker.security.JwtRequestAuthenticator;
import com.beertracker.service.AnalyticsService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller providing personal consumption time-series analytics and streak metrics
 * computed in the authenticated user's IANA timezone ({@code REQ-F-012}).
 */
@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final JwtRequestAuthenticator jwtRequestAuthenticator;

    /**
     * Constructs the analytics controller with required service and JWT authenticator.
     *
     * @param analyticsService        service computing hourly profiles and streaks
     * @param jwtRequestAuthenticator Bearer JWT extractor and verifier
     */
    public AnalyticsController(
            AnalyticsService analyticsService,
            JwtRequestAuthenticator jwtRequestAuthenticator
    ) {
        this.analyticsService = analyticsService;
        this.jwtRequestAuthenticator = jwtRequestAuthenticator;
    }

    /**
     * Returns aggregated consumption statistics and a 24-slot hourly profile for the user.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @param date       optional exact date filter ({@code YYYY-MM-DD})
     * @param dateFrom   optional inclusive start date filter ({@code YYYY-MM-DD})
     * @param dateTo     optional inclusive end date filter ({@code YYYY-MM-DD})
     * @return {@code 200 OK} with {@link ConsumptionAnalyticsResponse}
     */
    @GetMapping("/analytics/consumption")
    public ResponseEntity<ConsumptionAnalyticsResponse> getConsumptionAnalytics(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "date_from", required = false) String dateFrom,
            @RequestParam(value = "date_to", required = false) String dateTo
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(analyticsService.getConsumptionAnalytics(userId, date, dateFrom, dateTo));
    }

    /**
     * Returns the authenticated user's current and longest consecutive consumption day streaks.
     *
     * @param authHeader HTTP {@code Authorization} header carrying a valid Bearer token
     * @return {@code 200 OK} with {@link StreakResponse}
     */
    @GetMapping("/streaks")
    public ResponseEntity<StreakResponse> getStreaks(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = jwtRequestAuthenticator.requireUserId(authHeader);
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(analyticsService.getStreaks(userId));
    }
}
