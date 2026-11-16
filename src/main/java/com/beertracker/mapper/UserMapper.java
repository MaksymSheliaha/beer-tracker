package com.beertracker.mapper;

import com.beertracker.dto.UserRegisterRequest;
import com.beertracker.dto.UserResponse;
import com.beertracker.model.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps between {@link User} entities and registration/response DTOs, ensuring
 * {@code password_hash} is never exposed externally ({@code REQ-NF-006}).
 */
@Component
public class UserMapper {

    /**
     * Converts a validated registration request and salted BCrypt hash into a {@link User} entity.
     *
     * @param request      registration payload
     * @param passwordHash BCrypt-encoded password hash (cost &gt;= 12)
     * @return new {@link User} entity assigned the default {@code USER} role
     */
    public User toEntity(UserRegisterRequest request, String passwordHash) {
        return new User(
                UUID.randomUUID(),
                request.email().trim().toLowerCase(Locale.ROOT),
                passwordHash,
                "USER",
                request.timezone().trim(),
                Instant.now()
        );
    }

    /**
     * Maps a {@link User} entity to its sanitized public {@link UserResponse} DTO.
     *
     * @param user persisted user entity
     * @return public user profile DTO without password credentials
     */
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.id(),
                user.email(),
                user.role(),
                user.timezone(),
                user.createdAt()
        );
    }
}
