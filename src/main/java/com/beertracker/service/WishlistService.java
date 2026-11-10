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
public class WishlistService {

    private static final Logger log = LoggerFactory.getLogger(WishlistService.class);

    private final WishlistRepository wishlistRepository;
    private final BeerRepository beerRepository;
    private final WishlistMapper wishlistMapper;

    public WishlistService(
            WishlistRepository wishlistRepository,
            BeerRepository beerRepository,
            WishlistMapper wishlistMapper
    ) {
        this.wishlistRepository = wishlistRepository;
        this.beerRepository = beerRepository;
        this.wishlistMapper = wishlistMapper;
    }

    public synchronized WishlistItemResponse addToWishlist(UUID userId, UUID beerId) {
        Beer beer = beerRepository.findById(beerId)
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

        log.info("Beer added to user wishlist",
                kv("event", "wishlist_item_added"),
                kv("wishlist_item_id", item.id().toString()),
                kv("user_id", userId.toString()),
                kv("beer_id", beerId.toString()),
                kv("status", "201"));

        return wishlistMapper.toResponse(item, beer);
    }

    public WishlistPagedResponse listWishlist(UUID userId, int page, int size) {
        if (!DomainValidationPredicates.IS_VALID_PAGE_INDEX.test(page)
                || !DomainValidationPredicates.IS_VALID_PAGE_SIZE.test(size)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Invalid pagination parameters",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
            );
        }

        List<WishlistItemResponse> allItems = wishlistRepository.findByUser(userId).stream()
                .map(item -> wishlistMapper.toResponse(item, beerRepository.findById(item.beerId()).orElse(null)))
                .toList();

        return wishlistMapper.toPagedResponse(allItems, page, size);
    }

    public synchronized void removeFromWishlist(UUID userId, UUID beerId) {
        boolean removed = wishlistRepository.removeByUserAndBeer(userId, beerId);
        if (!removed) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "WISHLIST_ITEM_NOT_FOUND",
                    "Beer was not found in the user's wishlist",
                    List.of(new ErrorDetail("beer_id", "No wishlist item found for beer " + beerId))
            );
        }

        log.info("Beer removed from user wishlist",
                kv("event", "wishlist_item_removed"),
                kv("user_id", userId.toString()),
                kv("beer_id", beerId.toString()),
                kv("status", "204"));
    }
}
