package com.beertracker.mapper;

import com.beertracker.dto.WishlistItemResponse;
import com.beertracker.dto.WishlistPagedResponse;
import com.beertracker.model.Beer;
import com.beertracker.model.WishlistItem;
import com.beertracker.validation.PaginationHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps between {@link WishlistItem} entities and paginated REST response DTOs ({@code REQ-F-009}).
 */
@Component
public class WishlistMapper {

    private final BeerMapper beerMapper;

    /**
     * Constructs the wishlist mapper with the catalog beer mapper.
     *
     * @param beerMapper mapper converting embedded {@link Beer} entities into {@code BeerResponse}
     */
    public WishlistMapper(BeerMapper beerMapper) {
        this.beerMapper = beerMapper;
    }

    /**
     * Maps a {@link WishlistItem} and its associated {@link Beer} into a {@link WishlistItemResponse}.
     *
     * @param item wishlist entity
     * @param beer associated catalog beer (may be {@code null} if unavailable)
     * @return public wishlist item DTO
     */
    public WishlistItemResponse toResponse(WishlistItem item, Beer beer) {
        return new WishlistItemResponse(
                item.id(),
                item.beerId(),
                item.addedAt(),
                beer != null ? beerMapper.toResponse(beer) : null
        );
    }

    /**
     * Wraps wishlist item DTOs into a paginated {@link WishlistPagedResponse} container.
     *
     * @param allItems complete ordered list of user wishlist items
     * @param page     zero-based page index
     * @param size     page size
     * @return paginated container DTO
     */
    public WishlistPagedResponse toPagedResponse(List<WishlistItemResponse> allItems, int page, int size) {
        PaginationHelper.PageSlice<WishlistItemResponse> slice = PaginationHelper.slice(allItems, page, size);
        return new WishlistPagedResponse(
                slice.items(),
                slice.page(),
                slice.size(),
                slice.totalElements(),
                slice.totalPages()
        );
    }
}
