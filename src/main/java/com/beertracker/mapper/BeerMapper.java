package com.beertracker.mapper;

import com.beertracker.dto.BeerCreateRequest;
import com.beertracker.dto.BeerPackagingCreateRequest;
import com.beertracker.dto.BeerPackagingResponse;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class BeerMapper {

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

    public BeerPackaging toPackagingEntity(UUID beerId, BeerPackagingCreateRequest pkgReq) {
        return new BeerPackaging(
                UUID.randomUUID(),
                beerId,
                pkgReq.volumeMl(),
                pkgReq.packageType(),
                pkgReq.barcode() != null ? pkgReq.barcode().trim() : null
        );
    }

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

    public BeerPackagingResponse toPackagingResponse(BeerPackaging packaging) {
        return new BeerPackagingResponse(
                packaging.id(),
                packaging.beerId(),
                packaging.volumeMl(),
                packaging.packageType(),
                packaging.barcode()
        );
    }

    public BeerPagedResponse toPagedResponse(List<BeerResponse> allMatching, int page, int size) {
        int totalElements = allMatching.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<BeerResponse> pageItems = allMatching.subList(fromIndex, toIndex);

        return new BeerPagedResponse(
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
