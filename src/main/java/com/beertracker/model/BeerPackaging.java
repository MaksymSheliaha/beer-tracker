package com.beertracker.model;

import java.util.UUID;

public record BeerPackaging(
        UUID id,
        UUID beerId,
        Integer volumeMl,
        String packageType,
        String barcode
) {
}
