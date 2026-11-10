package com.beertracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "consumption_entries")
public class ConsumptionEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "beer_id", nullable = false)
    private UUID beerId;

    @Column(name = "packaging_id", nullable = false)
    private UUID packagingId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "total_volume_ml", nullable = false)
    private Integer totalVolumeMl;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "wishlist_autoremoved", nullable = false)
    private boolean wishlistAutoremoved;

    @Column(name = "collection_added", nullable = false)
    private boolean collectionAdded;

    @Column(name = "beer_name", nullable = false)
    private String beerName;

    @Column(name = "packaging_volume_ml", nullable = false)
    private Integer packagingVolumeMl;

    protected ConsumptionEntry() {
    }

    public ConsumptionEntry(
            UUID id,
            UUID userId,
            UUID beerId,
            UUID packagingId,
            Integer quantity,
            Integer totalVolumeMl,
            Instant consumedAt,
            Instant createdAt,
            boolean wishlistAutoremoved,
            boolean collectionAdded,
            String beerName,
            Integer packagingVolumeMl
    ) {
        this.id = id;
        this.userId = userId;
        this.beerId = beerId;
        this.packagingId = packagingId;
        this.quantity = quantity;
        this.totalVolumeMl = totalVolumeMl;
        this.consumedAt = consumedAt;
        this.createdAt = createdAt;
        this.wishlistAutoremoved = wishlistAutoremoved;
        this.collectionAdded = collectionAdded;
        this.beerName = beerName;
        this.packagingVolumeMl = packagingVolumeMl;
    }

    public ConsumptionEntry(
            UUID id,
            UUID beerId,
            UUID packagingId,
            Integer quantity,
            Integer totalVolumeMl,
            Instant consumedAt,
            Instant createdAt,
            boolean wishlistAutoremoved,
            boolean collectionAdded,
            String beerName,
            Integer packagingVolumeMl
    ) {
        this(
                id,
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                beerId,
                packagingId,
                quantity,
                totalVolumeMl,
                consumedAt,
                createdAt,
                wishlistAutoremoved,
                collectionAdded,
                beerName,
                packagingVolumeMl
        );
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID beerId() {
        return beerId;
    }

    public UUID packagingId() {
        return packagingId;
    }

    public Integer quantity() {
        return quantity;
    }

    public Integer totalVolumeMl() {
        return totalVolumeMl;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean wishlistAutoremoved() {
        return wishlistAutoremoved;
    }

    public boolean collectionAdded() {
        return collectionAdded;
    }

    public String beerName() {
        return beerName;
    }

    public Integer packagingVolumeMl() {
        return packagingVolumeMl;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getBeerId() {
        return beerId;
    }

    public void setBeerId(UUID beerId) {
        this.beerId = beerId;
    }

    public UUID getPackagingId() {
        return packagingId;
    }

    public void setPackagingId(UUID packagingId) {
        this.packagingId = packagingId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getTotalVolumeMl() {
        return totalVolumeMl;
    }

    public void setTotalVolumeMl(Integer totalVolumeMl) {
        this.totalVolumeMl = totalVolumeMl;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isWishlistAutoremoved() {
        return wishlistAutoremoved;
    }

    public void setWishlistAutoremoved(boolean wishlistAutoremoved) {
        this.wishlistAutoremoved = wishlistAutoremoved;
    }

    public boolean isCollectionAdded() {
        return collectionAdded;
    }

    public void setCollectionAdded(boolean collectionAdded) {
        this.collectionAdded = collectionAdded;
    }

    public String getBeerName() {
        return beerName;
    }

    public void setBeerName(String beerName) {
        this.beerName = beerName;
    }

    public Integer getPackagingVolumeMl() {
        return packagingVolumeMl;
    }

    public void setPackagingVolumeMl(Integer packagingVolumeMl) {
        this.packagingVolumeMl = packagingVolumeMl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsumptionEntry that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
