package com.beertracker.service;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.ReviewCreateRequest;
import com.beertracker.dto.ReviewResponse;
import com.beertracker.dto.ReviewUpdateRequest;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.ReviewMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.Review;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.ReviewRepository;
import com.beertracker.validation.DomainValidationPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service managing verified tasting {@link Review} creation, updates, archival, and
 * recalculation of beer rating aggregates ({@code REQ-F-008}).
 */
@Service
public class ReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final BeerRepository beerRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ReviewMapper reviewMapper;
    private final ReadCommittedTxExecutor txExecutor;

    /**
     * Constructs the review service.
     *
     * @param reviewRepository      repository managing reviews
     * @param beerRepository        repository storing target beer aggregates
     * @param consumptionRepository repository verifying prior user consumption
     * @param reviewMapper          mapper between {@link Review} and public DTOs
     * @param txExecutor            ACID {@code READ_COMMITTED} transaction executor
     */
    public ReviewService(
            ReviewRepository reviewRepository,
            BeerRepository beerRepository,
            ConsumptionRepository consumptionRepository,
            ReviewMapper reviewMapper,
            ReadCommittedTxExecutor txExecutor
    ) {
        this.reviewRepository = reviewRepository;
        this.beerRepository = beerRepository;
        this.consumptionRepository = consumptionRepository;
        this.reviewMapper = reviewMapper;
        this.txExecutor = txExecutor;
    }

    /**
     * Creates a single active review for a beer after verifying that the user has recorded
     * at least one prior consumption entry ({@code AC-008.1}..{@code AC-008.3}).
     *
     * @param beerId  target beer UUID
     * @param userId  authenticated author UUID
     * @param request rating and optional review text
     * @return created {@link ReviewResponse}
     * @throws ApiException with {@code 403 FORBIDDEN} ({@code ERR_CONSUMPTION_REQUIRED}) if not consumed,
     *                      {@code 404 NOT_FOUND} if the beer does not exist or is inactive,
     *                      {@code 409 CONFLICT} ({@code REVIEW_ALREADY_EXISTS}) if an active review exists,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} for invalid rating values
     */
    public synchronized ReviewResponse createReview(UUID beerId, UUID userId, ReviewCreateRequest request) {
        long startNs = System.nanoTime();
        validateRating(request == null ? null : request.rating());

        return txExecutor.execute(() -> {
            Beer beer = beerRepository.findActiveById(beerId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "BEER_NOT_FOUND",
                            "Beer with specified identifier was not found",
                            List.of(new ErrorDetail("id", "No beer found with id " + beerId))
                    ));

            if (!consumptionRepository.hasUserConsumedBeer(userId, beerId)) {
                String message = "Review creation denied: "
                        + "You must log at least one consumption entry for this beer before leaving a review";
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ERR_CONSUMPTION_REQUIRED",
                        message,
                        List.of(new ErrorDetail("beer_id", "No consumption entries found for user and beer " + beerId))
                );
            }

            if (reviewRepository.findActiveByBeerAndUser(beerId, userId).isPresent()) {
                String message = "Active review for this beer already exists for the current user. "
                        + "Use PUT to update it.";
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "REVIEW_ALREADY_EXISTS",
                        message,
                        List.of(new ErrorDetail("beer_id", "Only one active review per user and beer is allowed"))
                );
            }

            Review created = reviewRepository.save(reviewMapper.toNewEntity(beerId, userId, request));
            recalculateBeerRatingAggregates(beer);
            double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;

            LOG.info("Verified review created",
                    kv("event", "review_created"),
                    kv("review_id", created.id().toString()),
                    kv("beer_id", beerId.toString()),
                    kv("user_id", userId.toString()),
                    kv("rating", created.rating()),
                    kv("duration_ms", durationMs),
                    kv("status", "201"));

            return reviewMapper.toResponse(created);
        });
    }

    /**
     * Updates the authenticated user's existing active review for a beer and refreshes rating aggregates.
     *
     * @param beerId  target beer UUID
     * @param userId  authenticated author UUID
     * @param request updated rating and review text
     * @return updated {@link ReviewResponse}
     * @throws ApiException with {@code 404 NOT_FOUND} if the beer or active review is missing,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} for invalid rating values
     */
    public synchronized ReviewResponse updateReview(UUID beerId, UUID userId, ReviewUpdateRequest request) {
        long startNs = System.nanoTime();
        validateRating(request == null ? null : request.rating());

        return txExecutor.execute(() -> {
            Beer beer = beerRepository.findActiveById(beerId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "BEER_NOT_FOUND",
                            "Beer with specified identifier was not found",
                            List.of(new ErrorDetail("id", "No beer found with id " + beerId))
                    ));

            Review existing = reviewRepository.findActiveByBeerAndUser(beerId, userId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "REVIEW_NOT_FOUND",
                            "Active review for this beer was not found for the current user",
                            List.of(new ErrorDetail("beer_id", "No active review found for user and beer " + beerId))
                    ));

            Review updated = new Review(
                    existing.id(),
                    existing.beerId(),
                    existing.userId(),
                    request.rating(),
                    request.reviewText(),
                    false,
                    null,
                    existing.createdAt(),
                    Instant.now()
            );
            reviewRepository.save(updated);
            recalculateBeerRatingAggregates(beer);
            double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;

            LOG.info("Verified review updated",
                    kv("event", "review_updated"),
                    kv("review_id", updated.id().toString()),
                    kv("beer_id", beerId.toString()),
                    kv("user_id", userId.toString()),
                    kv("rating", updated.rating()),
                    kv("duration_ms", durationMs),
                    kv("status", "200"));

            return reviewMapper.toResponse(updated);
        });
    }

    /**
     * Soft-archives the user's active review and recalculates the beer's average rating.
     *
     * @param beerId target beer UUID
     * @param userId authenticated author UUID
     * @throws ApiException with {@code 404 NOT_FOUND} if the beer or active review does not exist
     */
    public synchronized void archiveReview(UUID beerId, UUID userId) {
        txExecutor.executeVoid(() -> {
            Beer beer = beerRepository.findActiveById(beerId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "BEER_NOT_FOUND",
                            "Beer with specified identifier was not found",
                            List.of(new ErrorDetail("id", "No beer found with id " + beerId))
                    ));

            Review existing = reviewRepository.findActiveByBeerAndUser(beerId, userId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "REVIEW_NOT_FOUND",
                            "Active review for this beer was not found for the current user",
                            List.of(new ErrorDetail("beer_id", "No active review found for user and beer " + beerId))
                    ));

            Review archived = new Review(
                    existing.id(),
                    existing.beerId(),
                    existing.userId(),
                    existing.rating(),
                    existing.reviewText(),
                    true,
                    Instant.now(),
                    existing.createdAt(),
                    Instant.now()
            );
            reviewRepository.save(archived);
            recalculateBeerRatingAggregates(beer);

            LOG.info("Verified review soft-archived",
                    kv("event", "review_archived"),
                    kv("review_id", archived.id().toString()),
                    kv("beer_id", beerId.toString()),
                    kv("user_id", userId.toString()),
                    kv("status", "204"));
        });
    }

    private void validateRating(Double rating) {
        if (!DomainValidationPredicates.IS_VALID_REVIEW_RATING.test(rating)) {
            String issue = "Allowed values are 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0";
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RATING_VALUE",
                    "Rating must be between 1.0 and 5.0 with a 0.5 step increment",
                    List.of(new ErrorDetail("rating", issue))
            );
        }
    }

    private void recalculateBeerRatingAggregates(Beer beer) {
        List<Review> activeReviews = reviewRepository.findActiveByBeer(beer.id());
        int count = activeReviews.size();
        Double roundedAvg = count == 0
                ? null
                : Math.round(activeReviews.stream().mapToDouble(Review::rating).average().orElse(0.0) * 100.0) / 100.0;

        Beer updatedBeer = new Beer(
                beer.id(),
                beer.name(),
                beer.brewery(),
                beer.style(),
                beer.abv(),
                beer.beerType(),
                beer.description(),
                beer.country(),
                beer.imageUrl(),
                beer.status(),
                roundedAvg,
                count,
                beer.packagings(),
                beer.createdAt()
        );
        beerRepository.save(updatedBeer);
    }
}
