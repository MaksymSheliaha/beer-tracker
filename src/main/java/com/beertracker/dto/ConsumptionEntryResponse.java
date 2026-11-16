package com.beertracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of an immutable {@code ConsumptionEntry} record, displaying
 * {@code "Видалений продукт"} when the referenced beer was soft-deleted
 * ({@code REQ-F-006}, {@code REQ-F-009}, {@code REQ-F-010}).
 *
 * @param id                  consumption entry UUID
 * @param beerId              consumed beer UUID
 * @param packagingId         consumed packaging UUID
 * @param quantity            consumed unit count
 * @param totalVolumeMl       server-calculated {@code quantity * packagingVolumeMl}
 * @param consumedAt          UTC consumption timestamp
 * @param createdAt           UTC server log creation timestamp
 * @param wishlistAutoremoved whether this entry removed the beer from the user's wishlist
 * @param collectionAdded     whether the beer is included in the user's tasted collection
 * @param beerName            display beer name (or {@code Placeholder Beer} label)
 * @param packagingVolumeMl   volume of one packaging unit in milliliters
 */
public record ConsumptionEntryResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("beer_id") UUID beerId,
        @JsonProperty("packaging_id") UUID packagingId,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("total_volume_ml") Integer totalVolumeMl,
        @JsonProperty("consumed_at") Instant consumedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("wishlist_autoremoved") boolean wishlistAutoremoved,
        @JsonProperty("collection_added") boolean collectionAdded,
        @JsonProperty("beer_name") String beerName,
        @JsonProperty("packaging_volume_ml") Integer packagingVolumeMl
) {
}
