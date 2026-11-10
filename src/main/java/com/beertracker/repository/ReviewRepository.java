package com.beertracker.repository;

import com.beertracker.model.Review;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    public synchronized int migrateAndDeduplicateReviews(UUID sourceBeerId, UUID targetBeerId) {
        List<Review> sourceActiveReviews = findActiveByBeer(sourceBeerId);
        int archivedCount = 0;
        Instant now = Instant.now();

        for (Review sourceReview : sourceActiveReviews) {
            Optional<Review> targetActiveOpt = findActiveByBeerAndUser(targetBeerId, sourceReview.userId());
            if (targetActiveOpt.isPresent()) {
                Review targetReview = targetActiveOpt.get();
                Instant sourceTime = sourceReview.updatedAt() != null ? sourceReview.updatedAt() : sourceReview.createdAt();
                Instant targetTime = targetReview.updatedAt() != null ? targetReview.updatedAt() : targetReview.createdAt();

                if (!sourceTime.isBefore(targetTime)) {
                    // Archive older target review and move newer source review to targetBeerId
                    Review archivedTarget = new Review(
                            targetReview.id(),
                            targetReview.beerId(),
                            targetReview.userId(),
                            targetReview.rating(),
                            targetReview.reviewText(),
                            true,
                            now,
                            targetReview.createdAt(),
                            now
                    );
                    reviewsById.put(archivedTarget.id(), archivedTarget);

                    Review migratedSource = new Review(
                            sourceReview.id(),
                            targetBeerId,
                            sourceReview.userId(),
                            sourceReview.rating(),
                            sourceReview.reviewText(),
                            false,
                            null,
                            sourceReview.createdAt(),
                            sourceReview.updatedAt()
                    );
                    reviewsById.put(migratedSource.id(), migratedSource);
                } else {
                    // Archive older source review and keep newer target review
                    Review archivedSource = new Review(
                            sourceReview.id(),
                            sourceReview.beerId(),
                            sourceReview.userId(),
                            sourceReview.rating(),
                            sourceReview.reviewText(),
                            true,
                            now,
                            sourceReview.createdAt(),
                            now
                    );
                    reviewsById.put(archivedSource.id(), archivedSource);
                }
                archivedCount++;
            } else {
                Review migratedSource = new Review(
                        sourceReview.id(),
                        targetBeerId,
                        sourceReview.userId(),
                        sourceReview.rating(),
                        sourceReview.reviewText(),
                        false,
                        null,
                        sourceReview.createdAt(),
                        sourceReview.updatedAt()
                );
                reviewsById.put(migratedSource.id(), migratedSource);
            }
        }
        return archivedCount;
    }
}
