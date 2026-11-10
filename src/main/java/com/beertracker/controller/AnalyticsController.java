package com.beertracker.controller;

import com.beertracker.dto.ConsumptionAnalyticsResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.StreakResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.service.AnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final JwtTokenProvider jwtTokenProvider;

    public AnalyticsController(AnalyticsService analyticsService, JwtTokenProvider jwtTokenProvider) {
        this.analyticsService = analyticsService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping("/analytics/consumption")
    public ResponseEntity<ConsumptionAnalyticsResponse> getConsumptionAnalytics(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "date_from", required = false) String dateFrom,
            @RequestParam(value = "date_to", required = false) String dateTo
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK)
                .body(analyticsService.getConsumptionAnalytics(userId, date, dateFrom, dateTo));
    }

    @GetMapping("/streaks")
    public ResponseEntity<StreakResponse> getStreaks(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = extractAuthenticatedUserId(authHeader);
        MDC.put("user_id", userId.toString());
        MDC.put("status", "200");

        return ResponseEntity.status(HttpStatus.OK).body(analyticsService.getStreaks(userId));
    }

    private UUID extractAuthenticatedUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Full authentication is required to access this resource",
                    List.of(new ErrorDetail("Authorization", "Missing or invalid Bearer JWT token"))
            );
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        JsonNode claims = jwtTokenProvider.validateAndParseToken(token)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Full authentication is required to access this resource",
                        List.of(new ErrorDetail("Authorization", "Missing or invalid Bearer JWT token"))
                ));
        try {
            return UUID.fromString(claims.path("sub").asText());
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Invalid subject in JWT token",
                    List.of(new ErrorDetail("Authorization", "Invalid user identifier in token"))
            );
        }
    }
}
