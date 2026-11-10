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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class ConsumptionService {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionService.class);

    private final ConsumptionRepository consumptionRepository;
    private final BeerRepository beerRepository;
    private final ConsumptionMapper consumptionMapper;

    public ConsumptionService(
            ConsumptionRepository consumptionRepository,
            BeerRepository beerRepository,
            ConsumptionMapper consumptionMapper
    ) {
        this.consumptionRepository = consumptionRepository;
        this.beerRepository = beerRepository;
        this.consumptionMapper = consumptionMapper;
    }

    public ConsumptionEntryResponse recordConsumption(ConsumptionEntryCreateRequest request) {
        long startNs = System.nanoTime();
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

        int totalVolumeMl = request.quantity() * packaging.volumeMl();
        ConsumptionEntry entry = new ConsumptionEntry(
                UUID.randomUUID(),
                beer.id(),
                packaging.id(),
                request.quantity(),
                totalVolumeMl,
                request.consumedAt(),
                Instant.now(),
                true,
                true,
                beer.name(),
                packaging.volumeMl()
        );

        consumptionRepository.append(entry);

        int totalVolumeSoFar = consumptionRepository.findAll().stream()
                .mapToInt(ConsumptionEntry::totalVolumeMl)
                .sum();
        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;

        log.info("Consumption entry created",
                kv("event", "consumption_entry_created"),
                kv("consumption_id", entry.id().toString()),
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
        if (page < 0 || size < 1 || size > 100) {
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

        int totalElements = all.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<ConsumptionEntryResponse> items = all.subList(fromIndex, toIndex);

        return new ConsumptionPagedResponse(
                items,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
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

        if (request.quantity() == null || request.quantity() < 1 || request.quantity() > 100) {
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
        if (request.consumedAt().isAfter(now)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CONSUMPTION_TIME_IN_FUTURE",
                    "consumed_at timestamp cannot be in the future",
                    List.of(new ErrorDetail("consumed_at", "Timestamp is ahead of current server time"))
            );
        }

        if (request.consumedAt().isBefore(now.minus(Duration.ofDays(365)))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "consumed_at timestamp cannot be older than 1 year",
                    List.of(new ErrorDetail("consumed_at", "Timestamp is older than 1 year"))
            );
        }
    }
}
