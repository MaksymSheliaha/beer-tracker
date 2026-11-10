package com.beertracker.mapper;

import com.beertracker.dto.BeerPackagingResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BeerMapper {

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
}
