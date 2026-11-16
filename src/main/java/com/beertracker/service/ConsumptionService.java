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
import com.beertracker.model.User;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.ConsumptionRepository;
import com.beertracker.repository.UserRepository;
import com.beertracker.repository.WishlistRepository;
import com.beertracker.validation.DomainValidationPredicates;
import com.beertracker.validation.PaginationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service managing the append-only {@link ConsumptionEntry} log, strict per-user data isolation,
 * temporal bounds validation, and transactional wishlist auto-removal
 * ({@code REQ-F-006}, {@code REQ-F-009}, {@code REQ-NF-005}, {@code REQ-NF-007}).
 */
@Service
public class ConsumptionService {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumptionService.class);

    private final ConsumptionRepository consumptionRepository;
    private final BeerRepository beerRepository;
    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ConsumptionMapper consumptionMapper;
    private final ReadCommittedTxExecutor txExecutor;

    /**
     * Constructs the consumption service.
     *
     * @param consumptionRepository repository storing append-only consumption records
     * @param beerRepository        repository storing catalog beers and packagings
     * @param wishlistRepository    repository storing personal wishlist entries
     * @param userRepository        repository supplying user registration timestamps
     * @param consumptionMapper     mapper between consumption entities and DTOs
     * @param txExecutor            ACID {@code READ_COMMITTED} transaction executor
     */
    public ConsumptionService(
            ConsumptionRepository consumptionRepository,
            BeerRepository beerRepository,
            WishlistRepository wishlistRepository,
            UserRepository userRepository,
            ConsumptionMapper consumptionMapper,
            ReadCommittedTxExecutor txExecutor
    ) {
        this.consumptionRepository = consumptionRepository;
        this.beerRepository = beerRepository;
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.consumptionMapper = consumptionMapper;
        this.txExecutor = txExecutor;
    }

    /**
     * Records a new immutable consumption entry for an authenticated user and atomically
     * deletes the consumed beer from the user's wishlist within the same ACID transaction
     * ({@code AC-006.1}..{@code AC-006.4}, {@code AC-009.2}).
     *
     * @param userId  authenticated owner UUID
     * @param request consumption payload specifying beer, packaging, quantity, and timestamp
     * @return created {@link ConsumptionEntryResponse} with server-calculated {@code total_volume_ml}
     * @throws ApiException with {@code 401 UNAUTHORIZED} if {@code userId} is missing,
     *                      {@code 404 NOT_FOUND} if the beer or packaging does not exist,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} if quantity or timestamp rules fail
     */
    public synchronized ConsumptionEntryResponse recordConsumption(
            UUID userId,
            ConsumptionEntryCreateRequest request
    ) {
        if (userId == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Full authentication is required to access this resource",
                    List.of(new ErrorDetail("Authorization", "Missing or invalid Bearer JWT token"))
            );
        }

        long startNs = System.nanoTime();
        validateConsumptionRequest(userId, request);

        return txExecutor.execute(() -> {
            Beer beer = beerRepository.findActiveById(request.beerId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "NOT_FOUND",
                            "Beer with specified identifier was not found",
                            List.of(new ErrorDetail("beer_id", "No product found with identifier " + request.beerId()))
                    ));

            BeerPackaging packaging = beer.packagings().stream()
                    .filter(p -> p.id().equals(request.packagingId()))
                    .findFirst()
                    .orElseThrow(() -> {
                        String issue = "No packaging found with identifier " + request.packagingId();
                        return new ApiException(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "Packaging with specified identifier was not found",
                                List.of(new ErrorDetail("packaging_id", issue))
                        );
                    });

            boolean wishlistAutoremoved = wishlistRepository.removeByUserAndBeer(userId, beer.id());

            ConsumptionEntry entry = consumptionMapper.toEntity(
                    request,
                    beer,
                    packaging,
                    userId,
                    wishlistAutoremoved
            );
            consumptionRepository.append(entry);

            int totalVolumeSoFar = consumptionRepository.findByUserId(userId).stream()
                    .mapToInt(ConsumptionEntry::totalVolumeMl)
                    .sum();
            double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;

            LOG.info("Consumption entry created",
                    kv("event", "consumption_entry_created"),
                    kv("consumption_id", entry.id().toString()),
                    kv("user_id", userId.toString()),
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
        });
    }

    /**
     * Lists paginated consumption entries belonging strictly to the authenticated user.
     *
     * @param userId authenticated owner UUID
     * @param page   zero-based page index
     * @param size   page size (1..100)
     * @return paginated {@link ConsumptionPagedResponse}
     */
    @Transactional(readOnly = true)
    public ConsumptionPagedResponse listEntries(UUID userId, int page, int size) {
        PaginationHelper.validate(page, size);

        List<ConsumptionEntryResponse> all = consumptionRepository.findByUserId(userId).stream()
                .map(consumptionMapper::toResponse)
                .toList();

        return consumptionMapper.toPagedResponse(all, page, size);
    }

    /**
     * Retrieves a single consumption entry owned by {@code userId}; returns {@code 404 Not Found}
     * if absent or owned by another user to prevent ID enumeration ({@code REQ-NF-005}).
     *
     * @param userId authenticated owner UUID
     * @param id     consumption entry UUID
     * @return {@link ConsumptionEntryResponse}
     * @throws ApiException with {@code 404 NOT_FOUND} ({@code CONSUMPTION_ENTRY_NOT_FOUND})
     */
    @Transactional(readOnly = true)
    public ConsumptionEntryResponse getEntryById(UUID userId, UUID id) {
        ConsumptionEntry entry = consumptionRepository.findById(id)
                .filter(e -> e.userId().equals(userId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CONSUMPTION_ENTRY_NOT_FOUND",
                        "Consumption entry with specified identifier was not found",
                        List.of(new ErrorDetail("id", "No accessible consumption entry found for provided ID"))
                ));
        return consumptionMapper.toResponse(entry);
    }

    private void validateConsumptionRequest(UUID userId, ConsumptionEntryCreateRequest request) {
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

        Optional<User> registeredUser = userRepository.findById(userId);
        if (registeredUser.isPresent() && request.consumedAt().isBefore(registeredUser.get().createdAt())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CONSUMPTION_TIME_BEFORE_REGISTRATION",
                    "consumed_at timestamp cannot precede user account registration",
                    List.of(new ErrorDetail("consumed_at", "Timestamp precedes account registration time"))
            );
        }
    }
}
