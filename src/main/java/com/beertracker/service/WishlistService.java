package com.beertracker.service;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.WishlistItemResponse;
import com.beertracker.dto.WishlistPagedResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.WishlistMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.WishlistItem;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.WishlistRepository;
import com.beertracker.validation.PaginationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service managing personal {@link WishlistItem} lists with strict per-user isolation
 * ({@code REQ-F-009}, {@code REQ-NF-005}).
 */
@Service
public class WishlistService {

    private static final Logger LOG = LoggerFactory.getLogger(WishlistService.class);

    private final WishlistRepository wishlistRepository;
    private final BeerRepository beerRepository;
    private final WishlistMapper wishlistMapper;
    private final ReadCommittedTxExecutor txExecutor;

    /**
     * Constructs the wishlist service.
     *
     * @param wishlistRepository repository storing personal wishlist items
     * @param beerRepository     repository verifying target beers exist and are active
     * @param wishlistMapper     mapper converting wishlist items to response DTOs
     * @param txExecutor         ACID {@code READ_COMMITTED} transaction executor
     */
    public WishlistService(
            WishlistRepository wishlistRepository,
            BeerRepository beerRepository,
            WishlistMapper wishlistMapper,
            ReadCommittedTxExecutor txExecutor
    ) {
        this.wishlistRepository = wishlistRepository;
        this.beerRepository = beerRepository;
        this.wishlistMapper = wishlistMapper;
        this.txExecutor = txExecutor;
    }

    /**
     * Adds an active catalog beer to the user's wishlist.
     *
     * @param userId authenticated owner UUID
     * @param beerId catalog beer UUID
     * @return created {@link WishlistItemResponse}
     * @throws ApiException with {@code 404 NOT_FOUND} if the beer is absent or inactive,
     *                      or {@code 409 CONFLICT} ({@code WISHLIST_ITEM_ALREADY_EXISTS}) on duplicate
     */
    public synchronized WishlistItemResponse addToWishlist(UUID userId, UUID beerId) {
        return txExecutor.execute(() -> {
            Beer beer = beerRepository.findActiveById(beerId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "BEER_NOT_FOUND",
                            "Beer with specified identifier was not found",
                            List.of(new ErrorDetail("beer_id", "No beer found with id " + beerId))
                    ));

            if (wishlistRepository.findByUserAndBeer(userId, beerId).isPresent()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "WISHLIST_ITEM_ALREADY_EXISTS",
                        "This beer is already in your wishlist",
                        List.of(new ErrorDetail("beer_id", "Duplicate wishlist entry for beer " + beerId))
                );
            }

            WishlistItem item = wishlistRepository.save(new WishlistItem(
                    UUID.randomUUID(),
                    userId,
                    beerId,
                    Instant.now()
            ));

            LOG.info("Beer added to user wishlist",
                    kv("event", "wishlist_item_added"),
                    kv("wishlist_item_id", item.id().toString()),
                    kv("user_id", userId.toString()),
                    kv("beer_id", beerId.toString()),
                    kv("status", "201"));

            return wishlistMapper.toResponse(item, beer);
        });
    }

    /**
     * Returns paginated wishlist entries belonging to the authenticated user.
     *
     * @param userId authenticated owner UUID
     * @param page   zero-based page index
     * @param size   page size (1..100)
     * @return paginated {@link WishlistPagedResponse}
     */
    @Transactional(readOnly = true)
    public WishlistPagedResponse listWishlist(UUID userId, int page, int size) {
        PaginationHelper.validate(page, size);

        List<WishlistItemResponse> allItems = wishlistRepository.findByUser(userId).stream()
                .map(item -> wishlistMapper.toResponse(item, beerRepository.findById(item.beerId()).orElse(null)))
                .toList();

        return wishlistMapper.toPagedResponse(allItems, page, size);
    }

    /**
     * Removes a beer from the authenticated user's wishlist; returns {@code 404 Not Found}
     * if not present to preserve user isolation ({@code REQ-NF-005}).
     *
     * @param userId authenticated owner UUID
     * @param beerId catalog beer UUID
     * @throws ApiException with {@code 404 NOT_FOUND} ({@code WISHLIST_ITEM_NOT_FOUND})
     */
    public synchronized void removeFromWishlist(UUID userId, UUID beerId) {
        txExecutor.executeVoid(() -> {
            boolean removed = wishlistRepository.removeByUserAndBeer(userId, beerId);
            if (!removed) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WISHLIST_ITEM_NOT_FOUND",
                        "Beer was not found in the user's wishlist",
                        List.of(new ErrorDetail("beer_id", "No wishlist item found for beer " + beerId))
                );
            }

            LOG.info("Beer removed from user wishlist",
                    kv("event", "wishlist_item_removed"),
                    kv("user_id", userId.toString()),
                    kv("beer_id", beerId.toString()),
                    kv("status", "204"));
        });
    }
}
