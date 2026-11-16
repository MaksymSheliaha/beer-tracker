package com.beertracker.service;

import com.beertracker.dto.CollectionPagedResponse;
import com.beertracker.dto.TastedCollectionItemResponse;
import com.beertracker.model.Beer;
import com.beertracker.model.ConsumptionEntry;
import com.beertracker.model.Review;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.ReviewRepository;
import com.beertracker.validation.PaginationHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service projecting a user's virtual {@code Tasted Collection} from append-only
 * {@link ConsumptionEntry} records and active review ratings ({@code REQ-F-009}, {@code REQ-NF-005}).
 */
@Service
@Transactional(readOnly = true)
public class CollectionService {

    private final ConsumptionRepository consumptionRepository;
    private final BeerRepository beerRepository;
    private final ReviewRepository reviewRepository;

    /**
     * Constructs the collection projection service.
     *
     * @param consumptionRepository repository of consumption logs
     * @param beerRepository        repository of catalog beers
     * @param reviewRepository      repository of active user reviews
     */
    public CollectionService(
            ConsumptionRepository consumptionRepository,
            BeerRepository beerRepository,
            ReviewRepository reviewRepository
    ) {
        this.consumptionRepository = consumptionRepository;
        this.beerRepository = beerRepository;
        this.reviewRepository = reviewRepository;
    }

    /**
     * Builds a paginated list of unique beers tasted by the specified user, aggregated by
     * total consumed volume, first/last consumption timestamps, and active personal rating.
     *
     * @param userId authenticated owner UUID
     * @param page   zero-based page index
     * @param size   page size (1..100)
     * @return paginated {@link CollectionPagedResponse} ordered by most recent consumption
     */
    public CollectionPagedResponse getTastedCollection(UUID userId, int page, int size) {
        PaginationHelper.validate(page, size);

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

        PaginationHelper.PageSlice<TastedCollectionItemResponse> slice = PaginationHelper.slice(allItems, page, size);

        return new CollectionPagedResponse(
                slice.items(),
                slice.page(),
                slice.size(),
                slice.totalElements(),
                slice.totalPages()
        );
    }
}
