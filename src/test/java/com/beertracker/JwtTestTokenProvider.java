package com.beertracker;

import com.beertracker.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Test helper generating signed HS256 JWT tokens for integration tests without invoking
 * the registration/login HTTP endpoints.
 */
public final class JwtTestTokenProvider {

    private static final JwtTokenProvider PROVIDER = new JwtTokenProvider(
            new ObjectMapper(),
            "beer-tracker-default-hmac-sha256-secret-key-2026-sdd-tdd"
    );

    private JwtTestTokenProvider() {
    }

    /**
     * Generates a synthetic JWT Access Token with the {@code USER} role.
     *
     * @param userId user UUID
     * @param email  user email claim
     * @return signed JWT Access Token string
     */
    public static String createUserToken(UUID userId, String email) {
        return PROVIDER.generateAccessToken(userId, email, "USER");
    }

    /**
     * Generates a synthetic JWT Access Token with the {@code MODERATOR} role.
     *
     * @param userId moderator UUID
     * @param email  moderator email claim
     * @return signed JWT Access Token string
     */
    public static String createModeratorToken(UUID userId, String email) {
        return PROVIDER.generateAccessToken(userId, email, "MODERATOR");
    }
}
