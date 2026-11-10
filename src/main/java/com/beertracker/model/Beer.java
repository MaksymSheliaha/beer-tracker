package com.beertracker.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Beer(
        UUID id,
        String name,
        String brewery,
        String style,
        Double abv,
        String beerType,
        String description,
        String country,
        String imageUrl,
        String status,
        Double ratingAvg,
        Integer reviewsCount,
        List<BeerPackaging> packagings,
        Instant createdAt
) {
}
