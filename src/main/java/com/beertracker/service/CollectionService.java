package com.beertracker.service;

import com.beertracker.dto.CollectionPagedResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.TastedCollectionItemResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.model.Beer;
import com.beertracker.model.ConsumptionEntry;
import com.beertracker.model.Review;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.ReviewRepository;
import com.beertracker.validation.DomainValidationPredicates;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CollectionService {

    private final ConsumptionRepository consumptionRepository;
    private final BeerRepository beerRepository;
    private final ReviewRepository reviewRepository;

    public CollectionService(
            ConsumptionRepository consumptionRepository,
            BeerRepository beerRepository,
            ReviewRepository reviewRepository
    ) {
        this.consumptionRepository = consumptionRepository;
        this.beerRepository = beerRepository;
        this.reviewRepository = reviewRepository;
    }

    public CollectionPagedResponse getTastedCollection(UUID userId, int page, int size) {
        if (!DomainValidationPredicates.IS_VALID_PAGE_INDEX.test(page)
                || !DomainValidationPredicates.IS_VALID_PAGE_SIZE.test(size)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Invalid pagination parameters",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
            );
        }

        List<ConsumptionEntry> userEntries = consumptionRepository.findByUserId(userId);
        Map<UUID, List<ConsumptionEntry>> groupedByBeer = userEntries.stream()
                .collect(Collectors.groupingBy(
                        ConsumptionEntry::beerId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<TastedCollectionItemResponse> allItems = groupedByBeer.entrySet().stream()
                .map(entry -> {
                    UUID beerId = entry.getKey();
                    List<ConsumptionEntry> logs = entry.getValue();
                    Beer beer = beerRepository.findById(beerId).orElse(null);
                    String beerName = beer != null ? beer.name() : logs.getFirst().beerName();
                    String brewery = beer != null ? beer.brewery() : "";
                    int totalVolumeMl = logs.stream().mapToInt(ConsumptionEntry::totalVolumeMl).sum();
                    Instant firstConsumedAt = logs.stream()
                            .map(ConsumptionEntry::consumedAt)
                            .min(Comparator.naturalOrder())
                            .orElse(Instant.now());
                    Instant lastConsumedAt = logs.stream()
                            .map(ConsumptionEntry::consumedAt)
                            .max(Comparator.naturalOrder())
                            .orElse(Instant.now());
                    Double userRating = reviewRepository.findActiveByBeerAndUser(beerId, userId)
                            .map(Review::rating)
                            .orElse(null);

                    return new TastedCollectionItemResponse(
                            beerId,
                            beerName,
                            brewery,
                            totalVolumeMl,
                            firstConsumedAt,
                            lastConsumedAt,
                            userRating
                    );
                })
                .sorted(Comparator.comparing(TastedCollectionItemResponse::lastConsumedAt).reversed())
                .toList();

        int totalElements = allItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<TastedCollectionItemResponse> pageItems = allItems.subList(fromIndex, toIndex);

        return new CollectionPagedResponse(
                pageItems,
                page,
                size,
                totalElements,
                totalPages
        );
    }
}
