package com.beertracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a physical packaging variant ({@code BOTTLE}, {@code CAN},
 * {@code DRAFT}, {@code KEG}) for a {@link Beer} with fixed volume in ml and optional unique
 * barcode ({@code REQ-F-001}, {@code REQ-F-002}).
 */
@Entity
@Table(name = "beer_packagings")
public class BeerPackaging {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beer_id", nullable = false)
    private Beer beer;

    @Column(name = "beer_id", insertable = false, updatable = false)
    private UUID beerId;

    @Column(name = "volume_ml", nullable = false)
    private Integer volumeMl;

    @Column(name = "package_type", nullable = false)
    private String packageType;

    @Column(name = "barcode", unique = true)
    private String barcode;

    protected BeerPackaging() {
    }

    /**
     * Constructs a packaging variant bound to a parent beer identifier.
     *
     * @param id          packaging UUID
     * @param beerId      parent beer UUID
     * @param volumeMl    container volume in milliliters ({@code 50..10000})
     * @param packageType packaging category ({@code BOTTLE}, {@code CAN}, {@code DRAFT}, {@code KEG})
     * @param barcode     optional unique numeric barcode (8..14 digits)
     */
    public BeerPackaging(
            UUID id,
            UUID beerId,
            Integer volumeMl,
            String packageType,
            String barcode
    ) {
        this.id = id;
        this.beerId = beerId;
        this.volumeMl = volumeMl;
        this.packageType = packageType;
        this.barcode = barcode;
    }

    public UUID id() {
        return id;
    }

    public UUID beerId() {
        return beerId != null ? beerId : (beer != null ? beer.id() : null);
    }

    public Integer volumeMl() {
        return volumeMl;
    }

    public String packageType() {
        return packageType;
    }

    public String barcode() {
        return barcode;
    }

    /**
     * Returns the owning catalog beer entity.
     *
     * @return the owning beer
     */
    public Beer getBeer() {
        return beer;
    }

    /**
     * Associates this packaging variant with the owning beer and synchronizes {@code beerId}.
     *
     * @param beer owning beer entity
     */
    public void setBeer(Beer beer) {
        this.beer = beer;
        if (beer != null) {
            this.beerId = beer.id();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBeerId() {
        return beerId();
    }

    public void setBeerId(UUID beerId) {
        this.beerId = beerId;
    }

    public Integer getVolumeMl() {
        return volumeMl;
    }

    public void setVolumeMl(Integer volumeMl) {
        this.volumeMl = volumeMl;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BeerPackaging that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
