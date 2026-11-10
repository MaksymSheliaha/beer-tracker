package com.beertracker.mapper;

import com.beertracker.dto.ReviewCreateRequest;
import com.beertracker.dto.ReviewResponse;
import com.beertracker.model.Review;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ReviewMapper {

    public Review toNewEntity(UUID beerId, UUID userId, ReviewCreateRequest request) {
        Instant now = Instant.now();
        return new Review(
                UUID.randomUUID(),
                beerId,
                userId,
                request.rating(),
                request.reviewText(),
                false,
                null,
                now,
                now
        );
    }

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.id(),
                review.beerId(),
                review.userId(),
                review.rating(),
                review.reviewText(),
                review.createdAt(),
                review.updatedAt()
        );
    }
}
