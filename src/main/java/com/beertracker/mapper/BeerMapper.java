package com.beertracker.mapper;

import com.beertracker.dto.BeerCreateRequest;
import com.beertracker.dto.BeerPackagingCreateRequest;
import com.beertracker.dto.BeerPackagingResponse;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.dto.BeerVersionCreateRequest;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.validation.PaginationHelper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maps between {@link Beer} / {@link BeerPackaging} JPA entities, creation/version payloads,
 * and public REST response DTOs ({@code REQ-F-001}, {@code REQ-F-003}).
 */
@Component
public class BeerMapper {

    /**
     * Converts a validated beer registration request into a new {@code UNVERIFIED} {@link Beer} entity.
     *
     * @param request validated beer creation payload
     * @return newly instantiated {@link Beer} entity with generated UUIDs
     */
    public Beer toEntity(BeerCreateRequest request) {
        UUID beerId = UUID.randomUUID();
        List<BeerPackaging> packagings = request.packagings().stream()
                .map(pkg -> toPackagingEntity(beerId, pkg))
                .toList();

        return new Beer(
                beerId,
                request.name().trim(),
                request.brewery().trim(),
                request.style().trim(),
                request.abv(),
                request.beerType(),
                request.description(),
                request.country(),
                request.imageUrl(),
                "UNVERIFIED",
                null,
                0,
                List.copyOf(packagings),
                Instant.now()
        );
    }

    /**
     * Creates a {@link BeerPackaging} child entity bound to the specified parent beer identifier.
     *
     * @param beerId parent beer UUID
     * @param pkgReq packaging specification payload
     * @return new {@link BeerPackaging} entity
     */
    public BeerPackaging toPackagingEntity(UUID beerId, BeerPackagingCreateRequest pkgReq) {
        return new BeerPackaging(
                UUID.randomUUID(),
                beerId,
                pkgReq.volumeMl(),
                pkgReq.packageType(),
                pkgReq.barcode() != null ? pkgReq.barcode().trim() : null
        );
    }

    /**
     * Merges proposed attributes and additional packagings from a {@link BeerVersionCreateRequest}
     * over a base {@link Beer} snapshot, assigning {@code targetStatus}.
     *
     * @param baseBeer     existing beer entity
     * @param payload      proposed attribute overrides and extra packagings (may be {@code null})
     * @param targetStatus resulting status to set on the returned {@link Beer}
     * @return new {@link Beer} instance combining base fields with non-null payload overrides
     */
    public Beer mergeWithVersionPayload(Beer baseBeer, BeerVersionCreateRequest payload, String targetStatus) {
        List<BeerPackaging> updatedPackagings = new ArrayList<>(baseBeer.packagings());
        if (payload != null && payload.packagings() != null && !payload.packagings().isEmpty()) {
            for (BeerPackagingCreateRequest pkgReq : payload.packagings()) {
                updatedPackagings.add(toPackagingEntity(baseBeer.id(), pkgReq));
            }
        }
        return new Beer(
                baseBeer.id(),
                payload != null && payload.name() != null ? payload.name().trim() : baseBeer.name(),
                payload != null && payload.brewery() != null ? payload.brewery().trim() : baseBeer.brewery(),
                payload != null && payload.style() != null ? payload.style().trim() : baseBeer.style(),
                payload != null && payload.abv() != null ? payload.abv() : baseBeer.abv(),
                payload != null && payload.beerType() != null ? payload.beerType() : baseBeer.beerType(),
                payload != null && payload.description() != null ? payload.description() : baseBeer.description(),
                payload != null && payload.country() != null ? payload.country() : baseBeer.country(),
                payload != null && payload.imageUrl() != null ? payload.imageUrl() : baseBeer.imageUrl(),
                targetStatus,
                baseBeer.ratingAvg(),
                baseBeer.reviewsCount(),
                List.copyOf(updatedPackagings),
                baseBeer.createdAt()
        );
    }

    /**
     * Maps a {@link Beer} entity to its public {@link BeerResponse} DTO, omitting internal flags.
     *
     * @param beer catalog entity
     * @return public beer DTO
     */
    public BeerResponse toResponse(Beer beer) {
        List<BeerPackagingResponse> packagingResponses = beer.packagings().stream()
                .map(this::toPackagingResponse)
                .toList();
        return new BeerResponse(
                beer.id(),
                beer.name(),
                beer.brewery(),
                beer.style(),
                beer.abv(),
                beer.beerType(),
                beer.description(),
                beer.country(),
                beer.imageUrl(),
                beer.status(),
                beer.ratingAvg(),
                beer.reviewsCount(),
                packagingResponses,
                beer.createdAt()
        );
    }

    /**
     * Maps a {@link BeerPackaging} entity to its public {@link BeerPackagingResponse} DTO.
     *
     * @param packaging packaging entity
     * @return public packaging DTO
     */
    public BeerPackagingResponse toPackagingResponse(BeerPackaging packaging) {
        return new BeerPackagingResponse(
                packaging.id(),
                packaging.beerId(),
                packaging.volumeMl(),
                packaging.packageType(),
                packaging.barcode()
        );
    }

    /**
     * Builds a paginated {@link BeerPagedResponse} from a filtered list of beer DTOs.
     *
     * @param allMatching full list of matching beer DTOs
     * @param page        zero-based page index
     * @param size        page size
     * @return paginated container DTO
     */
    public BeerPagedResponse toPagedResponse(List<BeerResponse> allMatching, int page, int size) {
        PaginationHelper.PageSlice<BeerResponse> slice = PaginationHelper.slice(allMatching, page, size);
        return new BeerPagedResponse(
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
