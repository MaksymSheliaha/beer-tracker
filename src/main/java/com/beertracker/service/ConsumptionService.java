package com.beertracker.service;

import com.beertracker.dto.ConsumptionEntryCreateRequest;
import com.beertracker.dto.ConsumptionEntryResponse;
import com.beertracker.dto.ConsumptionPagedResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.ConsumptionMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.model.ConsumptionEntry;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.WishlistRepository;
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
public class ConsumptionService {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionService.class);
    private static final UUID ANONYMOUS_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ConsumptionRepository consumptionRepository;
    private final BeerRepository beerRepository;
    private final WishlistRepository wishlistRepository;
    private final ConsumptionMapper consumptionMapper;

    public ConsumptionService(
            ConsumptionRepository consumptionRepository,
            BeerRepository beerRepository,
            WishlistRepository wishlistRepository,
            ConsumptionMapper consumptionMapper
    ) {
        this.consumptionRepository = consumptionRepository;
        this.beerRepository = beerRepository;
        this.wishlistRepository = wishlistRepository;
        this.consumptionMapper = consumptionMapper;
    }

    public ConsumptionEntryResponse recordConsumption(ConsumptionEntryCreateRequest request) {
        return recordConsumption(ANONYMOUS_USER_ID, request);
    }

    public synchronized ConsumptionEntryResponse recordConsumption(UUID userId, ConsumptionEntryCreateRequest request) {
        long startNs = System.nanoTime();
        UUID effectiveUserId = userId != null ? userId : ANONYMOUS_USER_ID;
        validateConsumptionRequest(request);

        Beer beer = beerRepository.findById(request.beerId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Beer with specified identifier was not found",
                        List.of(new ErrorDetail("beer_id", "No product found with identifier " + request.beerId()))
                ));

        BeerPackaging packaging = beer.packagings().stream()
                .filter(p -> p.id().equals(request.packagingId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Packaging with specified identifier was not found",
                        List.of(new ErrorDetail("packaging_id", "No packaging found with identifier " + request.packagingId()))
                ));

        boolean wishlistAutoremoved = wishlistRepository.removeByUserAndBeer(effectiveUserId, beer.id());

        ConsumptionEntry entry = consumptionMapper.toEntity(
                request,
                beer,
                packaging,
                effectiveUserId,
                wishlistAutoremoved
        );
        consumptionRepository.append(entry);

        int totalVolumeSoFar = consumptionRepository.findAll().stream()
                .mapToInt(ConsumptionEntry::totalVolumeMl)
                .sum();
        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;

        log.info("Consumption entry created",
                kv("event", "consumption_entry_created"),
                kv("consumption_id", entry.id().toString()),
                kv("user_id", effectiveUserId.toString()),
                kv("beer_id", entry.beerId().toString()),
                kv("packaging_id", entry.packagingId().toString()),
                kv("volume_ml", packaging.volumeMl()),
                kv("quantity", entry.quantity()),
                kv("total_volume_so_far_ml", totalVolumeSoFar),
                kv("wishlist_autoremoved", entry.wishlistAutoremoved()),
                kv("collection_added", entry.collectionAdded()),
                kv("duration_ms", durationMs),
                kv("status", "201"));

        return consumptionMapper.toResponse(entry);
    }

    public ConsumptionPagedResponse listEntries(int page, int size) {
        if (!DomainValidationPredicates.IS_VALID_PAGE_INDEX.test(page)
                || !DomainValidationPredicates.IS_VALID_PAGE_SIZE.test(size)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Invalid pagination parameters",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
            );
        }

        List<ConsumptionEntryResponse> all = consumptionRepository.findAll().stream()
                .map(consumptionMapper::toResponse)
                .toList();

        return consumptionMapper.toPagedResponse(all, page, size);
    }

    public ConsumptionEntryResponse getEntryById(UUID id) {
        ConsumptionEntry entry = consumptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CONSUMPTION_ENTRY_NOT_FOUND",
                        "Consumption entry with specified identifier was not found",
                        List.of(new ErrorDetail("id", "No accessible consumption entry found for provided ID"))
                ));
        return consumptionMapper.toResponse(entry);
    }

    private void validateConsumptionRequest(ConsumptionEntryCreateRequest request) {
        if (request == null || request.beerId() == null || request.packagingId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "beer_id and packaging_id are required",
                    List.of(new ErrorDetail("beer_id", "Field is required"))
            );
        }

        if (!DomainValidationPredicates.IS_VALID_CONSUMPTION_QUANTITY.test(request.quantity())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_QUANTITY",
                    "Quantity must be an integer between 1 and 100",
                    List.of(new ErrorDetail("quantity", "Quantity must be between 1 and 100"))
            );
        }

        if (request.consumedAt() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "consumed_at is required",
                    List.of(new ErrorDetail("consumed_at", "Field is required"))
            );
        }

        Instant now = Instant.now();
        if (DomainValidationPredicates.IS_CONSUMED_AT_IN_FUTURE.test(request.consumedAt(), now)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CONSUMPTION_TIME_IN_FUTURE",
                    "consumed_at timestamp cannot be in the future",
                    List.of(new ErrorDetail("consumed_at", "Timestamp is ahead of current server time"))
            );
        }

        if (DomainValidationPredicates.IS_CONSUMED_AT_OLDER_THAN_ONE_YEAR.test(request.consumedAt(), now)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "consumed_at timestamp cannot be older than 1 year",
                    List.of(new ErrorDetail("consumed_at", "Timestamp is older than 1 year"))
            );
        }
    }
}
