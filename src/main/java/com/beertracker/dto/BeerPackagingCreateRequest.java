package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BeerPackagingCreateRequest(
        @JsonProperty("volume_ml") Integer volumeMl,
        @JsonProperty("package_type") String packageType,
        @JsonProperty("barcode") String barcode
) {
}
