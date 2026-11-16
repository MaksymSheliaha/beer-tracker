package com.beertracker.security;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.filter.TraceIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Extracts and validates Bearer JWT tokens from incoming HTTP {@code Authorization} headers.
 *
 * <p>Enforces stateless authentication ({@code REQ-NF-004}) and role-based access control
 * for standard users and moderators, while populating the SLF4J {@code user_id} MDC attribute
 * for structured audit logging ({@code REQ-NF-010}).</p>
 */
@Component
public class JwtRequestAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MODERATOR_ROLE = "MODERATOR";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Constructs the authenticator with the JWT token provider.
     *
     * @param jwtTokenProvider token parser and HMAC-SHA256 signature verifier
     */
    public JwtRequestAuthenticator(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Validates the {@code Authorization} header and returns the authenticated user identifier.
     *
     * @param authHeader raw HTTP {@code Authorization} header value (may be {@code null})
     * @return authenticated user UUID extracted from the {@code sub} claim
     * @throws ApiException with HTTP {@code 401 UNAUTHORIZED} if the token is missing, invalid, or expired
     */
    public UUID requireUserId(String authHeader) {
        JsonNode claims = parseClaims(authHeader);
        return extractSubjectUuid(claims);
    }

    /**
     * Validates the {@code Authorization} header, verifies the {@code MODERATOR} role,
     * and returns the authenticated moderator identifier.
     *
     * @param authHeader raw HTTP {@code Authorization} header value (may be {@code null})
     * @return authenticated moderator UUID extracted from the {@code sub} claim
     * @throws ApiException with HTTP {@code 401 UNAUTHORIZED} if authentication fails,
     *                      or {@code 403 FORBIDDEN} if the user does not hold the {@code MODERATOR} role
     */
    public UUID requireModeratorId(String authHeader) {
        JsonNode claims = parseClaims(authHeader);
        String role = claims.path("role").asText("USER");
        if (!MODERATOR_ROLE.equals(role)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "User does not have required MODERATOR role",
                    List.of(new ErrorDetail("role", "Access denied for role '" + role + "'"))
            );
        }
        return extractSubjectUuid(claims);
    }

    private JsonNode parseClaims(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw unauthorizedException("Missing or invalid Bearer JWT token");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return jwtTokenProvider.validateAndParseToken(token)
                .orElseThrow(() -> unauthorizedException("Missing or invalid Bearer JWT token"));
    }

    private UUID extractSubjectUuid(JsonNode claims) {
        try {
            UUID userId = UUID.fromString(claims.path("sub").asText());
            MDC.put(TraceIdFilter.MDC_USER_ID_KEY, userId.toString());
            return userId;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Invalid subject in JWT token",
                    List.of(new ErrorDetail("Authorization", "Invalid user identifier in token"))
            );
        }
    }

    private ApiException unauthorizedException(String issue) {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Full authentication is required to access this resource",
                List.of(new ErrorDetail("Authorization", issue))
        );
    }
}
