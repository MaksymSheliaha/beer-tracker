package com.beertracker.controller;

import com.beertracker.dto.RefreshTokenRequest;
import com.beertracker.dto.TokenResponse;
import com.beertracker.dto.UserLoginRequest;
import com.beertracker.dto.UserRegisterRequest;
import com.beertracker.dto.UserResponse;
import com.beertracker.service.AuthService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public REST controller handling user registration, credential authentication,
 * and one-time Refresh Token rotation under {@code /api/v1/auth} ({@code REQ-NF-004}, {@code REQ-NF-006}).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Constructs the authentication controller.
     *
     * @param authService service performing user registration and token issuance
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account with the default {@code USER} role and IANA timezone.
     *
     * @param request registration payload containing email, password, and IANA timezone
     * @return {@code 201 Created} with {@link UserResponse} (excluding password hashes)
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserRegisterRequest request) {
        MDC.put("status", "201");
        UserResponse created = authService.register(request);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.id()))
                .body(created);
    }

    /**
     * Authenticates a user by email and password and issues a JWT Access/Refresh token pair.
     *
     * @param request login credentials payload
     * @return {@code 200 OK} with {@link TokenResponse}
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody UserLoginRequest request) {
        MDC.put("status", "200");
        TokenResponse tokens = authService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(tokens);
    }

    /**
     * Rotates an active Refresh Token once and returns a fresh Access/Refresh token pair.
     *
     * @param request payload containing the previously issued {@code refresh_token}
     * @return {@code 200 OK} with rotated {@link TokenResponse}
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        MDC.put("status", "200");
        TokenResponse tokens = authService.refresh(request);
        return ResponseEntity.status(HttpStatus.OK).body(tokens);
    }
}
