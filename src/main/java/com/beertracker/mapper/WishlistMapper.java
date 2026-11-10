package com.beertracker.mapper;

import com.beertracker.dto.WishlistItemResponse;
import com.beertracker.dto.WishlistPagedResponse;
import com.beertracker.model.Beer;
import com.beertracker.model.WishlistItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WishlistMapper {

    private final BeerMapper beerMapper;

    public WishlistMapper(BeerMapper beerMapper) {
        this.beerMapper = beerMapper;
    }

    public WishlistItemResponse toResponse(WishlistItem item, Beer beer) {
        return new WishlistItemResponse(
                item.id(),
                item.beerId(),
                item.addedAt(),
                beer != null ? beerMapper.toResponse(beer) : null
        );
    }

    public WishlistPagedResponse toPagedResponse(List<WishlistItemResponse> allItems, int page, int size) {
        int totalElements = allItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<WishlistItemResponse> pageItems = allItems.subList(fromIndex, toIndex);

        return new WishlistPagedResponse(
                pageItems,
                page,
                size,
                totalElements,
                totalPages
        );
    }
}
