package com.beertracker.repository;

import com.beertracker.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findFirstByBeerIdAndUserIdAndIsArchivedFalse(UUID beerId, UUID userId);

    List<Review> findByBeerIdAndIsArchivedFalse(UUID beerId);

    default Optional<Review> findActiveByBeerAndUser(UUID beerId, UUID userId) {
        return findFirstByBeerIdAndUserIdAndIsArchivedFalse(beerId, userId);
    }

    default List<Review> findActiveByBeer(UUID beerId) {
        return findByBeerIdAndIsArchivedFalse(beerId);
    }

    default int migrateAndDeduplicateReviews(UUID sourceBeerId, UUID targetBeerId) {
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
                    targetReview.setArchived(true);
                    targetReview.setArchivedAt(now);
                    targetReview.setUpdatedAt(now);
                    saveAndFlush(targetReview);

                    sourceReview.setBeerId(targetBeerId);
                    saveAndFlush(sourceReview);
                } else {
                    sourceReview.setArchived(true);
                    sourceReview.setArchivedAt(now);
                    sourceReview.setUpdatedAt(now);
                    saveAndFlush(sourceReview);
                }
                archivedCount++;
            } else {
                sourceReview.setBeerId(targetBeerId);
                saveAndFlush(sourceReview);
            }
        }
        return archivedCount;
    }
}
