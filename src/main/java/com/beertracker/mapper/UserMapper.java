package com.beertracker.mapper;

import com.beertracker.dto.UserRegisterRequest;
import com.beertracker.dto.UserResponse;
import com.beertracker.model.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
public class UserMapper {

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
