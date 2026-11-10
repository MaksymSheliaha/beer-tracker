package com.beertracker.mapper;

import com.beertracker.dto.BeerVersionCreateRequest;
import com.beertracker.dto.BeerVersionPagedResponse;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.model.BeerVersion;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class BeerVersionMapper {

    public BeerVersion toEntity(UUID beerId, UUID authorId, BeerVersionCreateRequest payload) {
        return new BeerVersion(
                UUID.randomUUID(),
                beerId,
                authorId,
                payload,
                0,
                0,
                "UNVERIFIED",
                Instant.now()
        );
    }

    public BeerVersionResponse toResponse(BeerVersion version) {
        return new BeerVersionResponse(
                version.id(),
                version.beerId(),
                version.authorId(),
                version.payload(),
                version.confirmVotes(),
                version.rejectVotes(),
                version.status(),
                version.createdAt()
        );
    }

    public BeerVersionPagedResponse toPagedResponse(List<BeerVersionResponse> allMatching, int page, int size) {
        int totalElements = allMatching.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<BeerVersionResponse> pageItems = allMatching.subList(fromIndex, toIndex);

        return new BeerVersionPagedResponse(
                pageItems,
                page,
                size,
                totalElements,
                totalPages
        );
    }
}
