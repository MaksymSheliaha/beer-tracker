package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload describing a physical {@code BeerPackaging} variant ({@code REQ-F-001}).
 *
 * @param volumeMl    container volume in milliliters ({@code 50..10000})
 * @param packageType container type ({@code BOTTLE}, {@code CAN}, {@code DRAFT}, or {@code KEG})
 * @param barcode     optional unique numeric barcode (8..14 digits)
 */
public record BeerPackagingCreateRequest(
        @JsonProperty("volume_ml") Integer volumeMl,
        @JsonProperty("package_type") String packageType,
        @JsonProperty("barcode") String barcode
) {
}
