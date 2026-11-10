package com.beertracker.service;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.RefreshTokenRequest;
import com.beertracker.dto.TokenResponse;
import com.beertracker.dto.UserLoginRequest;
import com.beertracker.dto.UserRegisterRequest;
import com.beertracker.dto.UserResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.UserMapper;
import com.beertracker.model.RefreshToken;
import com.beertracker.model.User;
import com.beertracker.repository.RefreshTokenRepository;
import com.beertracker.repository.UserRepository;
import com.beertracker.security.JwtTokenProvider;
import com.beertracker.validation.DomainValidationPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public UserResponse register(UserRegisterRequest request) {
        validateRegistration(request);

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "User with email '%s' already exists".formatted(request.email().trim()),
                    List.of(new ErrorDetail("email", "Email address is already registered in the system"))
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = userMapper.toEntity(request, passwordHash);
        userRepository.save(user);

        log.info("User account registered",
                kv("event", "user_registered"),
                kv("user_id", user.id().toString()),
                kv("role", user.role()),
                kv("timezone", user.timezone()),
                kv("status", "201"));

        return userMapper.toResponse(user);
    }

    public TokenResponse login(UserLoginRequest request) {
        if (request == null
                || !DomainValidationPredicates.IS_NON_BLANK_TEXT.test(request.email())
                || !DomainValidationPredicates.IS_NON_BLANK_TEXT.test(request.password())) {
            throw invalidCredentialsException();
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(this::invalidCredentialsException);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            log.warn("Authentication failed due to invalid credentials",
                    kv("event", "security_violation_audit"),
                    kv("violation_type", "INVALID_CREDENTIALS"),
                    kv("status", "401"));
            throw invalidCredentialsException();
        }

        TokenResponse tokens = issueTokenPair(user);

        log.info("User authenticated via JWT",
                kv("event", "user_authenticated"),
                kv("user_id", user.id().toString()),
                kv("role", user.role()),
                kv("expires_in", tokens.expiresIn()),
                kv("status", "200"));

        return tokens;
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        if (request == null || !DomainValidationPredicates.IS_NON_BLANK_TEXT.test(request.refreshToken())) {
            throw invalidRefreshTokenException();
        }

        Instant now = Instant.now();
        RefreshToken consumed = refreshTokenRepository.consumeIfActive(request.refreshToken(), now)
                .orElseThrow(() -> {
                    log.warn("Refresh token rotation rejected (expired, invalid, or already used)",
                            kv("event", "security_violation_audit"),
                            kv("violation_type", "INVALID_REFRESH_TOKEN"),
                            kv("status", "401"));
                    return invalidRefreshTokenException();
                });

        User user = userRepository.findById(consumed.userId())
                .orElseThrow(this::invalidRefreshTokenException);

        TokenResponse rotatedTokens = issueTokenPair(user);

        log.info("Refresh token rotated once",
                kv("event", "token_refreshed"),
                kv("user_id", user.id().toString()),
                kv("status", "200"));

        return rotatedTokens;
    }

    private TokenResponse issueTokenPair(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.id(), user.email(), user.role());
        String refreshTokenValue = jwtTokenProvider.generateRefreshTokenValue();
        Instant expiresAt = Instant.now().plusSeconds(JwtTokenProvider.REFRESH_TOKEN_TTL_SECONDS);

        refreshTokenRepository.save(new RefreshToken(refreshTokenValue, user.id(), expiresAt, false));

        return new TokenResponse(
                accessToken,
                refreshTokenValue,
                "Bearer",
                JwtTokenProvider.ACCESS_TOKEN_TTL_SECONDS
        );
    }

    private void validateRegistration(UserRegisterRequest request) {
        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Registration payload is required",
                    List.of(new ErrorDetail("body", "Request body cannot be null"))
            );
        }

        List<ErrorDetail> details = new ArrayList<>();
        if (!DomainValidationPredicates.IS_VALID_EMAIL.test(request.email())) {
            details.add(new ErrorDetail("email", "Valid email address is required"));
        }
        if (!DomainValidationPredicates.IS_VALID_PASSWORD.test(request.password())) {
            details.add(new ErrorDetail("password", "Password must be at least 8 characters long"));
        }
        if (!DomainValidationPredicates.IS_VALID_IANA_TIMEZONE.test(request.timezone())) {
            details.add(new ErrorDetail("timezone", "Unknown IANA timezone '" + request.timezone() + "'"));
        }

        if (!details.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "Validation failed for registration parameters",
                    details
            );
        }
    }

    private ApiException invalidCredentialsException() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                List.of(new ErrorDetail("credentials", "Authentication failed for provided credentials"))
        );
    }

    private ApiException invalidRefreshTokenException() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "The provided refresh token is expired or revoked",
                List.of(new ErrorDetail("refresh_token", "Token signature expired or token already rotated"))
        );
    }
}
