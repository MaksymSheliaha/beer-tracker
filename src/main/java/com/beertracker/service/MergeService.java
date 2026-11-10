package com.beertracker.service;

import com.beertracker.dto.BeerMergeRequest;
import com.beertracker.dto.BeerMergeResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.model.Review;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.ReviewRepository;
import com.beertracker.repository.WishlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class MergeService {

    private static final Logger log = LoggerFactory.getLogger(MergeService.class);

    private final BeerRepository beerRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ReviewRepository reviewRepository;
    private final WishlistRepository wishlistRepository;

    public MergeService(
            BeerRepository beerRepository,
            ConsumptionRepository consumptionRepository,
            ReviewRepository reviewRepository,
            WishlistRepository wishlistRepository
    ) {
        this.beerRepository = beerRepository;
        this.consumptionRepository = consumptionRepository;
        this.reviewRepository = reviewRepository;
        this.wishlistRepository = wishlistRepository;
    }

    public synchronized BeerMergeResponse mergeBeers(BeerMergeRequest request) {
        long startNs = System.nanoTime();
        if (request == null || request.sourceBeerId() == null || request.targetBeerId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "source_beer_id and target_beer_id are required",
                    List.of(new ErrorDetail("source_beer_id", "Both source_beer_id and target_beer_id must be provided"))
            );
        }

        UUID sourceBeerId = request.sourceBeerId();
        UUID targetBeerId = request.targetBeerId();

        if (sourceBeerId.equals(targetBeerId)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CANNOT_MERGE_BEER_INTO_ITSELF",
                    "Source beer ID and Target beer ID must be distinct",
                    List.of(new ErrorDetail("target_beer_id", "Target beer cannot be identical to source beer"))
            );
        }

        Beer sourceBeer = beerRepository.findById(sourceBeerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Resource requested at /merges was not found",
                        List.of(new ErrorDetail("source_beer_id", "Source beer with specified identifier was not found"))
                ));

        Beer targetBeer = beerRepository.findById(targetBeerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Resource requested at /merges was not found",
                        List.of(new ErrorDetail("target_beer_id", "Target beer with specified identifier was not found"))
                ));

        // 1. Mark sourceBeer as MERGED first so its barcodes are released from BeerRepository index
        Beer mergedSourceBeer = new Beer(
                sourceBeer.id(),
                sourceBeer.name(),
                sourceBeer.brewery(),
                sourceBeer.style(),
                sourceBeer.abv(),
                sourceBeer.beerType(),
                sourceBeer.description(),
                sourceBeer.country(),
                sourceBeer.imageUrl(),
                "MERGED",
                sourceBeer.ratingAvg(),
                sourceBeer.reviewsCount(),
                sourceBeer.packagings(),
                sourceBeer.createdAt()
        );
        beerRepository.save(mergedSourceBeer);

        // 2. Migrate packagings from sourceBeer to targetBeer (AC-011.1)
        List<BeerPackaging> updatedTargetPackagings = new ArrayList<>(targetBeer.packagings());
        Map<UUID, UUID> packagingIdMapping = new HashMap<>();
        int migratedPackagingsCount = 0;

        for (BeerPackaging sourcePkg : sourceBeer.packagings()) {
            Optional<BeerPackaging> equivalentTargetPkg = updatedTargetPackagings.stream()
                    .filter(tp -> tp.packageType().equals(sourcePkg.packageType())
                            && tp.volumeMl().equals(sourcePkg.volumeMl()))
                    .findFirst();

            if (equivalentTargetPkg.isPresent()) {
                packagingIdMapping.put(sourcePkg.id(), equivalentTargetPkg.get().id());
            } else {
                BeerPackaging migratedPkg = new BeerPackaging(
                        UUID.randomUUID(),
                        targetBeerId,
                        sourcePkg.volumeMl(),
                        sourcePkg.packageType(),
                        sourcePkg.barcode()
                );
                updatedTargetPackagings.add(migratedPkg);
                packagingIdMapping.put(sourcePkg.id(), migratedPkg.id());
                migratedPackagingsCount++;
            }
        }

        // 3. Migrate consumption entries (AC-011.2)
        int migratedConsumptionsCount = consumptionRepository.migrateConsumptions(
                sourceBeerId,
                targetBeerId,
                targetBeer.name(),
                packagingIdMapping
        );

        // 4. Migrate and deduplicate reviews (AC-011.3)
        int archivedReviewsCount = reviewRepository.migrateAndDeduplicateReviews(sourceBeerId, targetBeerId);

        // 5. Remove duplicate wishlist entries for sourceBeer
        wishlistRepository.removeAllByBeer(sourceBeerId);

        // 6. Recalculate rating aggregates and save updated targetBeer
        List<Review> activeTargetReviews = reviewRepository.findActiveByBeer(targetBeerId);
        int reviewsCount = activeTargetReviews.size();
        Double ratingAvg = reviewsCount == 0
                ? null
                : Math.round(activeTargetReviews.stream().mapToDouble(Review::rating).average().orElse(0.0) * 100.0) / 100.0;

        Beer updatedTargetBeer = new Beer(
                targetBeer.id(),
                targetBeer.name(),
                targetBeer.brewery(),
                targetBeer.style(),
                targetBeer.abv(),
                targetBeer.beerType(),
                targetBeer.description(),
                targetBeer.country(),
                targetBeer.imageUrl(),
                targetBeer.status(),
                ratingAvg,
                reviewsCount,
                List.copyOf(updatedTargetPackagings),
                targetBeer.createdAt()
        );
        beerRepository.save(updatedTargetBeer);

        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;
        log.info("Catalog duplicate merge completed",
                kv("event", "catalog_merge_completed"),
                kv("source_beer_id", sourceBeerId.toString()),
                kv("target_beer_id", targetBeerId.toString()),
                kv("migrated_consumptions_count", migratedConsumptionsCount),
                kv("migrated_packagings_count", migratedPackagingsCount),
                kv("archived_reviews_count", archivedReviewsCount),
                kv("duration_ms", durationMs),
                kv("status", "200"));

        return new BeerMergeResponse(
                targetBeerId,
                migratedConsumptionsCount,
                migratedPackagingsCount,
                archivedReviewsCount,
                "MERGED"
        );
    }
}
