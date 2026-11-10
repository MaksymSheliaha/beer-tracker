package com.beertracker.mapper;

import com.beertracker.dto.ConsumptionEntryResponse;
import com.beertracker.model.ConsumptionEntry;
import org.springframework.stereotype.Component;

@Component
public class ConsumptionMapper {

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
}
