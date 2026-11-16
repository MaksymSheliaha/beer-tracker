package com.beertracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a user's tasting review and rating ({@code 1.0..5.0} step {@code 0.5})
 * for a catalog {@link Beer} ({@code REQ-F-008}, {@code REQ-F-011}).
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "beer_id", nullable = false)
    private UUID beerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rating", nullable = false)
    private Double rating;

    @Column(name = "review_text", length = 2000)
    private String reviewText;

    @Column(name = "is_archived", nullable = false)
    private boolean isArchived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Review() {
    }

    /**
     * Constructs a {@link Review} entity with rating, optional text, and archival metadata.
     *
     * @param id         review UUID
     * @param beerId     reviewed beer UUID
     * @param userId     author user UUID
     * @param rating     numeric score ({@code 1.0..5.0} in {@code 0.5} steps)
     * @param reviewText optional tasting notes (max 2000 characters)
     * @param isArchived whether the review has been soft-archived or superseded by merge
     * @param archivedAt optional UTC archival timestamp
     * @param createdAt  UTC creation timestamp
     * @param updatedAt  UTC last-update timestamp
     */
    public Review(
            UUID id,
            UUID beerId,
            UUID userId,
            Double rating,
            String reviewText,
            boolean isArchived,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.beerId = beerId;
        this.userId = userId;
        this.rating = rating;
        this.reviewText = reviewText;
        this.isArchived = isArchived;
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID beerId() {
        return beerId;
    }

    public UUID userId() {
        return userId;
    }

    public Double rating() {
        return rating;
    }

    public String reviewText() {
        return reviewText;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBeerId() {
        return beerId;
    }

    public void setBeerId(UUID beerId) {
        this.beerId = beerId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getReviewText() {
        return reviewText;
    }

    public void setReviewText(String reviewText) {
        this.reviewText = reviewText;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Review review)) {
            return false;
        }
        return Objects.equals(id, review.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
