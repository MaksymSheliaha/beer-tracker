package com.beertracker;

import com.beertracker.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public final class JwtTestTokenProvider {

    private static final JwtTokenProvider PROVIDER = new JwtTokenProvider(
            new ObjectMapper(),
            "beer-tracker-default-hmac-sha256-secret-key-2026-sdd-tdd"
    );

    private JwtTestTokenProvider() {
    }

    public static String createUserToken(UUID userId, String email) {
        return PROVIDER.generateAccessToken(userId, email, "USER");
    }

    public static String createModeratorToken(UUID userId, String email) {
        return PROVIDER.generateAccessToken(userId, email, "MODERATOR");
    }
}
