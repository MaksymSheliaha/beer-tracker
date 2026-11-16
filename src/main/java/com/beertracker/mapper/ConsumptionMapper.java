package com.beertracker.mapper;

import com.beertracker.dto.ConsumptionEntryCreateRequest;
import com.beertracker.dto.ConsumptionEntryResponse;
import com.beertracker.dto.ConsumptionPagedResponse;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.model.ConsumptionEntry;
import com.beertracker.validation.PaginationHelper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Maps between {@link ConsumptionEntry} entities and REST request/response DTOs ({@code REQ-F-006}).
 */
@Component
public class ConsumptionMapper {

    /**
     * Converts a validated consumption creation request into a persisted {@link ConsumptionEntry},
     * computing {@code totalVolumeMl = quantity * packaging.volumeMl()}.
     *
     * @param request             validated consumption payload
     * @param beer                target catalog beer
     * @param packaging           selected packaging variant
     * @param userId              authenticated owner UUID
     * @param wishlistAutoremoved whether recording this entry removed the beer from the user's wishlist
     * @return immutable {@link ConsumptionEntry} entity
     */
    public ConsumptionEntry toEntity(
            ConsumptionEntryCreateRequest request,
            Beer beer,
            BeerPackaging packaging,
            UUID userId,
            boolean wishlistAutoremoved
    ) {
        int totalVolumeMl = request.quantity() * packaging.volumeMl();
        return new ConsumptionEntry(
                UUID.randomUUID(),
                userId,
                beer.id(),
                packaging.id(),
                request.quantity(),
                totalVolumeMl,
                request.consumedAt(),
                Instant.now(),
                wishlistAutoremoved,
                true,
                beer.name(),
                packaging.volumeMl()
        );
    }

    /**
     * Maps a {@link ConsumptionEntry} entity to its public {@link ConsumptionEntryResponse} DTO.
     *
     * @param entry persisted consumption entity
     * @return public consumption DTO
     */
    public ConsumptionEntryResponse toResponse(ConsumptionEntry entry) {
        return new ConsumptionEntryResponse(
                entry.id(),
                entry.beerId(),
                entry.packagingId(),
                entry.quantity(),
                entry.totalVolumeMl(),
                entry.consumedAt(),
                entry.createdAt(),
                entry.wishlistAutoremoved(),
                entry.collectionAdded(),
                entry.beerName(),
                entry.packagingVolumeMl()
        );
    }

    /**
     * Wraps a user's consumption entries into a paginated {@link ConsumptionPagedResponse} DTO.
     *
     * @param allEntries full list of user consumption DTOs
     * @param page       zero-based page index
     * @param size       page size
     * @return paginated container DTO
     */
    public ConsumptionPagedResponse toPagedResponse(List<ConsumptionEntryResponse> allEntries, int page, int size) {
        PaginationHelper.PageSlice<ConsumptionEntryResponse> slice = PaginationHelper.slice(allEntries, page, size);
        return new ConsumptionPagedResponse(
                slice.items(),
                slice.page(),
                slice.size(),
                slice.totalElements(),
                slice.totalPages(),
                slice.hasNext(),
                slice.hasPrevious()
        );
    }
}
