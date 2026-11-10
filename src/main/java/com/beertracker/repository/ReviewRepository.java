package com.beertracker.repository;

import com.beertracker.model.Review;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ReviewRepository {

    private final Map<UUID, Review> reviewsById = new ConcurrentHashMap<>();

    public Review save(Review review) {
        reviewsById.put(review.id(), review);
        return review;
    }

    public Optional<Review> findActiveByBeerAndUser(UUID beerId, UUID userId) {
        return reviewsById.values().stream()
                .filter(r -> !r.isArchived() && beerId.equals(r.beerId()) && userId.equals(r.userId()))
                .findFirst();
    }

    public List<Review> findActiveByBeer(UUID beerId) {
        return reviewsById.values().stream()
                .filter(r -> !r.isArchived() && beerId.equals(r.beerId()))
                .toList();
    }
}
