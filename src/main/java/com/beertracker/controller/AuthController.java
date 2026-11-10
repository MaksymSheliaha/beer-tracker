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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserRegisterRequest request) {
        MDC.put("status", "201");
        UserResponse created = authService.register(request);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.id()))
                .body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody UserLoginRequest request) {
        MDC.put("status", "200");
        TokenResponse tokens = authService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        MDC.put("status", "200");
        TokenResponse tokens = authService.refresh(request);
        return ResponseEntity.status(HttpStatus.OK).body(tokens);
    }
}
