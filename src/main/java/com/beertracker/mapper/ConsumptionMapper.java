package com.beertracker.mapper;

import com.beertracker.dto.ConsumptionEntryCreateRequest;
import com.beertracker.dto.ConsumptionEntryResponse;
import com.beertracker.dto.ConsumptionPagedResponse;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.model.ConsumptionEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ConsumptionMapper {

    public ConsumptionEntry toEntity(ConsumptionEntryCreateRequest request, Beer beer, BeerPackaging packaging) {
        int totalVolumeMl = request.quantity() * packaging.volumeMl();
        return new ConsumptionEntry(
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
    }

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

    public ConsumptionPagedResponse toPagedResponse(List<ConsumptionEntryResponse> allEntries, int page, int size) {
        int totalElements = allEntries.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<ConsumptionEntryResponse> pageItems = allEntries.subList(fromIndex, toIndex);

        return new ConsumptionPagedResponse(
                pageItems,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
    }
}
