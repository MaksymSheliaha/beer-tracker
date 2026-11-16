package com.beertracker.repository;

import com.beertracker.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Review} entities enforcing the single-active-review
 * invariant and merge-time deduplication ({@code REQ-F-008}, {@code REQ-F-011}).
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /**
     * Finds the single active (non-archived) review by beer and author.
     *
     * @param beerId beer UUID
     * @param userId author user UUID
     * @return active review if present
     */
    Optional<Review> findFirstByBeerIdAndUserIdAndIsArchivedFalse(UUID beerId, UUID userId);

    /**
     * Finds all active (non-archived) reviews for the specified beer.
     *
     * @param beerId beer UUID
     * @return list of active reviews
     */
    List<Review> findByBeerIdAndIsArchivedFalse(UUID beerId);

    /**
     * Alias for {@link #findFirstByBeerIdAndUserIdAndIsArchivedFalse(UUID, UUID)}.
     *
     * @param beerId beer UUID
     * @param userId author user UUID
     * @return active review if present
     */
    default Optional<Review> findActiveByBeerAndUser(UUID beerId, UUID userId) {
        return findFirstByBeerIdAndUserIdAndIsArchivedFalse(beerId, userId);
    }

    /**
     * Alias for {@link #findByBeerIdAndIsArchivedFalse(UUID)}.
     *
     * @param beerId beer UUID
     * @return active reviews for the beer
     */
    default List<Review> findActiveByBeer(UUID beerId) {
        return findByBeerIdAndIsArchivedFalse(beerId);
    }

    /**
     * Migrates active reviews from a merged source beer to a target beer; when the same user
     * authored reviews on both beers, archives the older review ({@code AC-011.3}).
     *
     * @param sourceBeerId source duplicate beer UUID
     * @param targetBeerId canonical target beer UUID
     * @return number of older duplicate reviews archived during migration
     */
    default int migrateAndDeduplicateReviews(UUID sourceBeerId, UUID targetBeerId) {
        List<Review> sourceActiveReviews = findActiveByBeer(sourceBeerId);
        int archivedCount = 0;
        Instant now = Instant.now();

        for (Review sourceReview : sourceActiveReviews) {
            Optional<Review> targetActiveOpt = findActiveByBeerAndUser(targetBeerId, sourceReview.userId());
            if (targetActiveOpt.isPresent()) {
                Review targetReview = targetActiveOpt.get();
                Instant sourceTime = sourceReview.updatedAt() != null
                        ? sourceReview.updatedAt()
                        : sourceReview.createdAt();
                Instant targetTime = targetReview.updatedAt() != null
                        ? targetReview.updatedAt()
                        : targetReview.createdAt();

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
