package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Public representation of a catalog beer packaging variant ({@code REQ-F-001}).
 *
 * @param id          packaging UUID
 * @param beerId      parent beer UUID
 * @param volumeMl    container volume in milliliters
 * @param packageType container type ({@code BOTTLE}, {@code CAN}, {@code DRAFT}, {@code KEG})
 * @param barcode     optional numeric barcode
 */
public record BeerPackagingResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("volume_ml") Integer volumeMl,
        @JsonProperty("package_type") String packageType,
        @JsonProperty("barcode") String barcode
) {
}
