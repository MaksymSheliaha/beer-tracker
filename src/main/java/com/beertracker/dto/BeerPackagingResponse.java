package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record BeerPackagingResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("volume_ml") Integer volumeMl,
        @JsonProperty("package_type") String packageType,
        @JsonProperty("barcode") String barcode
) {
}
