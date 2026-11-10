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

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final BeerRepository beerRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ReviewMapper reviewMapper;

    public ReviewService(
            ReviewRepository reviewRepository,
            BeerRepository beerRepository,
            ConsumptionRepository consumptionRepository,
            ReviewMapper reviewMapper
    ) {
        this.reviewRepository = reviewRepository;
        this.beerRepository = beerRepository;
        this.consumptionRepository = consumptionRepository;
        this.reviewMapper = reviewMapper;
    }

    public synchronized ReviewResponse createReview(UUID beerId, UUID userId, ReviewCreateRequest request) {
        long startNs = System.nanoTime();
        Beer beer = beerRepository.findById(beerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BEER_NOT_FOUND",
                        "Beer with specified identifier was not found",
                        List.of(new ErrorDetail("id", "No beer found with id " + beerId))
                ));

        validateRating(request == null ? null : request.rating());

        if (!consumptionRepository.hasUserConsumedBeer(userId, beerId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ERR_CONSUMPTION_REQUIRED",
                    "Review creation denied: You must log at least one consumption entry for this beer before leaving a review",
                    List.of(new ErrorDetail("beer_id", "No consumption entries found for user and beer " + beerId))
            );
        }

        if (reviewRepository.findActiveByBeerAndUser(beerId, userId).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REVIEW_ALREADY_EXISTS",
                    "Active review for this beer already exists for the current user. Use PUT to update it.",
                    List.of(new ErrorDetail("beer_id", "Only one active review per user and beer is allowed"))
            );
        }

        Review created = reviewRepository.save(reviewMapper.toNewEntity(beerId, userId, request));
        recalculateBeerRatingAggregates(beer);
        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;

        log.info("Verified review created",
                kv("event", "review_created"),
                kv("review_id", created.id().toString()),
                kv("beer_id", beerId.toString()),
                kv("user_id", userId.toString()),
                kv("rating", created.rating()),
                kv("duration_ms", durationMs),
                kv("status", "201"));

        return reviewMapper.toResponse(created);
    }

    public synchronized ReviewResponse updateReview(UUID beerId, UUID userId, ReviewUpdateRequest request) {
        long startNs = System.nanoTime();
        Beer beer = beerRepository.findById(beerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BEER_NOT_FOUND",
                        "Beer with specified identifier was not found",
                        List.of(new ErrorDetail("id", "No beer found with id " + beerId))
                ));

        validateRating(request == null ? null : request.rating());

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

        log.info("Verified review updated",
                kv("event", "review_updated"),
                kv("review_id", updated.id().toString()),
                kv("beer_id", beerId.toString()),
                kv("user_id", userId.toString()),
                kv("rating", updated.rating()),
                kv("duration_ms", durationMs),
                kv("status", "200"));

        return reviewMapper.toResponse(updated);
    }

    public synchronized void archiveReview(UUID beerId, UUID userId) {
        Beer beer = beerRepository.findById(beerId)
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

        log.info("Verified review soft-archived",
                kv("event", "review_archived"),
                kv("review_id", archived.id().toString()),
                kv("beer_id", beerId.toString()),
                kv("user_id", userId.toString()),
                kv("status", "204"));
    }

    private void validateRating(Double rating) {
        if (!DomainValidationPredicates.IS_VALID_REVIEW_RATING.test(rating)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RATING_VALUE",
                    "Rating must be between 1.0 and 5.0 with a 0.5 step increment",
                    List.of(new ErrorDetail("rating", "Allowed values are 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0"))
            );
        }
    }

    private void recalculateBeerRatingAggregates(Beer beer) {
        List<Review> activeReviews = reviewRepository.findActiveByBeer(beer.id());
        int count = activeReviews.size();
        double avg = count == 0 ? 0.0 : activeReviews.stream().mapToDouble(Review::rating).average().orElse(0.0);
        double roundedAvg = Math.round(avg * 100.0) / 100.0;

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
