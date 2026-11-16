package com.beertracker.mapper;

import com.beertracker.dto.ReviewCreateRequest;
import com.beertracker.dto.ReviewResponse;
import com.beertracker.model.Review;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps between {@link Review} entities and public REST DTOs, omitting internal archival flags
 * ({@code REQ-F-008}, {@code SRS-ADD-001}).
 */
@Component
public class ReviewMapper {

    /**
     * Creates a new active (non-archived) {@link Review} entity from a creation request.
     *
     * @param beerId  target beer UUID
     * @param userId  author user UUID
     * @param request validated rating and optional review text
     * @return newly created {@link Review} entity
     */
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

    /**
     * Maps a {@link Review} entity to its public {@link ReviewResponse} DTO without archival flags.
     *
     * @param review persisted review entity
     * @return public review DTO
     */
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
