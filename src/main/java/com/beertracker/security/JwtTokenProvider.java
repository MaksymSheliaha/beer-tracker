package com.beertracker.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final int ACCESS_TOKEN_TTL_SECONDS = 900; // 15 minutes (REQ-NF-004)
    public static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 60 * 60; // 30 days (REQ-NF-004)
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secretKeyBytes;

    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${security.jwt.secret:beer-tracker-default-hmac-sha256-secret-key-2026-sdd-tdd}") String secret
    ) {
        this.objectMapper = objectMapper;
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        long iat = Instant.now().getEpochSecond();
        long exp = iat + ACCESS_TOKEN_TTL_SECONDS;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId.toString());
        payload.put("email", email);
        payload.put("role", role);
        payload.put("iat", iat);
        payload.put("exp", exp);

        try {
            String encodedHeader = base64UrlEncode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = signHmacSha256(signingInput);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT access token", e);
        }
    }

    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString();
    }

    public Optional<JsonNode> validateAndParseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = signHmacSha256(signingInput);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                return Optional.empty();
            }
            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(decodedPayload);
            long exp = claims.path("exp").asLong(0L);
            if (Instant.now().getEpochSecond() >= exp) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String signHmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec keySpec = new SecretKeySpec(secretKeyBytes, HMAC_SHA256);
        mac.init(keySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(rawHmac);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
