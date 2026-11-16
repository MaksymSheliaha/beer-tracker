package com.beertracker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security bean configuration providing salted BCrypt password hashing with work factor
 * {@value #BCRYPT_STRENGTH} ({@code REQ-NF-006}).
 */
@Configuration
public class SecurityConfig {

    /**
     * Minimum BCrypt cost factor mandated by {@code REQ-NF-006}.
     */
    public static final int BCRYPT_STRENGTH = 12;

    /**
     * Exposes a {@link BCryptPasswordEncoder} bean configured with cost factor 12.
     *
     * @return configured {@link PasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
