package com.beertracker.mapper;

import com.beertracker.dto.BeerVersionCreateRequest;
import com.beertracker.dto.BeerVersionPagedResponse;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.model.BeerVersion;
import com.beertracker.validation.PaginationHelper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Maps between {@link BeerVersion} entities, proposal payloads, and public response DTOs
 * ({@code REQ-F-003}).
 */
@Component
public class BeerVersionMapper {

    /**
     * Instantiates a new {@code UNVERIFIED} {@link BeerVersion} entity with zero vote counters.
     *
     * @param beerId   target beer UUID
     * @param authorId UUID of the proposing user
     * @param payload  proposed attribute and packaging updates
     * @return newly initialized {@link BeerVersion} entity
     */
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

    /**
     * Maps a {@link BeerVersion} entity to its public {@link BeerVersionResponse} DTO.
     *
     * @param version version entity
     * @return public version DTO
     */
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

    /**
     * Wraps matching {@link BeerVersionResponse} items into a paginated container DTO.
     *
     * @param allMatching complete filtered list of version DTOs
     * @param page        zero-based page index
     * @param size        page size
     * @return paginated version container DTO
     */
    public BeerVersionPagedResponse toPagedResponse(List<BeerVersionResponse> allMatching, int page, int size) {
        PaginationHelper.PageSlice<BeerVersionResponse> slice = PaginationHelper.slice(allMatching, page, size);
        return new BeerVersionPagedResponse(
                slice.items(),
                slice.page(),
                slice.size(),
                slice.totalElements(),
                slice.totalPages()
        );
    }
}
