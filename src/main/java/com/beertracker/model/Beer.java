package com.beertracker.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical catalog entity representing a beer product ({@code PACKAGED} or {@code DRAFT})
 * with lifecycle verification status, aggregated rating metrics, and associated packagings
 * ({@code REQ-F-001}, {@code REQ-F-010}, {@code REQ-F-011}).
 */
@Entity
@Table(name = "beers")
public class Beer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "brewery", nullable = false)
    private String brewery;

    @Column(name = "style", nullable = false)
    private String style;

    @Column(name = "abv", nullable = false)
    private Double abv;

    @Column(name = "beer_type", nullable = false)
    private String beerType;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "country")
    private String country;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "rating_avg")
    private Double ratingAvg;

    @Column(name = "reviews_count")
    private Integer reviewsCount;

    @OneToMany(mappedBy = "beer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BeerPackaging> packagings = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Beer() {
    }

    /**
     * Constructs a {@link Beer} catalog entity and defensively copies its packagings,
     * releasing barcodes automatically when transitioning into {@code MERGED} or
     * {@code PLACEHOLDER} status.
     *
     * @param id           stable beer UUID
     * @param name         product name
     * @param brewery      brewery name
     * @param style        beer style
     * @param abv          alcohol by volume percentage ({@code 0.0..100.0})
     * @param beerType     product category ({@code PACKAGED} or {@code DRAFT})
     * @param description  optional tasting/catalog description
     * @param country      optional ISO 3166-1 alpha-2 country code
     * @param imageUrl     optional label image URI
     * @param status       lifecycle status ({@code UNVERIFIED}, {@code VERIFIED}, {@code REJECTED},
     *                     {@code MERGED}, {@code PLACEHOLDER})
     * @param ratingAvg    aggregated average rating from active reviews (nullable)
     * @param reviewsCount number of active reviews
     * @param packagings   list of physical packaging variants
     * @param createdAt    UTC creation timestamp
     */
    public Beer(
            UUID id,
            String name,
            String brewery,
            String style,
            Double abv,
            String beerType,
            String description,
            String country,
            String imageUrl,
            String status,
            Double ratingAvg,
            Integer reviewsCount,
            List<BeerPackaging> packagings,
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.brewery = brewery;
        this.style = style;
        this.abv = abv;
        this.beerType = beerType;
        this.description = description;
        this.country = country;
        this.imageUrl = imageUrl;
        this.status = status;
        this.ratingAvg = ratingAvg;
        this.reviewsCount = reviewsCount;
        this.createdAt = createdAt;
        this.packagings = new ArrayList<>();
        if (packagings != null) {
            boolean releaseBarcodes = "MERGED".equals(status) || "PLACEHOLDER".equals(status);
            for (BeerPackaging pkg : packagings) {
                BeerPackaging copy = new BeerPackaging(
                        pkg.id(),
                        this.id,
                        pkg.volumeMl(),
                        pkg.packageType(),
                        releaseBarcodes ? null : pkg.barcode()
                );
                copy.setBeer(this);
                this.packagings.add(copy);
            }
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String brewery() {
        return brewery;
    }

    public String style() {
        return style;
    }

    public Double abv() {
        return abv;
    }

    public String beerType() {
        return beerType;
    }

    public String description() {
        return description;
    }

    public String country() {
        return country;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public String status() {
        return status;
    }

    public Double ratingAvg() {
        return ratingAvg;
    }

    public Integer reviewsCount() {
        return reviewsCount;
    }

    public List<BeerPackaging> packagings() {
        return packagings;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrewery() {
        return brewery;
    }

    public void setBrewery(String brewery) {
        this.brewery = brewery;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public Double getAbv() {
        return abv;
    }

    public void setAbv(Double abv) {
        this.abv = abv;
    }

    public String getBeerType() {
        return beerType;
    }

    public void setBeerType(String beerType) {
        this.beerType = beerType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getRatingAvg() {
        return ratingAvg;
    }

    public void setRatingAvg(Double ratingAvg) {
        this.ratingAvg = ratingAvg;
    }

    public Integer getReviewsCount() {
        return reviewsCount;
    }

    public void setReviewsCount(Integer reviewsCount) {
        this.reviewsCount = reviewsCount;
    }

    public List<BeerPackaging> getPackagings() {
        return packagings;
    }

    public void setPackagings(List<BeerPackaging> packagings) {
        this.packagings = packagings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Beer beer)) {
            return false;
        }
        return Objects.equals(id, beer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
