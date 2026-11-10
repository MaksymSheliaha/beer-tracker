package com.beertracker.model;

import com.beertracker.dto.BeerVersionCreateRequest;

import java.time.Instant;
import java.util.UUID;

public record BeerVersion(
        UUID id,
        UUID beerId,
        UUID authorId,
        BeerVersionCreateRequest payload,
        int confirmVotes,
        int rejectVotes,
        String status,
        Instant createdAt
) {
}
