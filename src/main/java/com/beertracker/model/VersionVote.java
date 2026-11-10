package com.beertracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "version_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "version_id"})
)
public class VersionVote {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", insertable = false, updatable = false)
    private BeerVersion version;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vote_type", nullable = false)
    private String voteType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VersionVote() {
    }

    public VersionVote(
            UUID id,
            UUID versionId,
            UUID userId,
            String voteType,
            Instant createdAt
    ) {
        this.id = id;
        this.versionId = versionId;
        this.userId = userId;
        this.voteType = voteType;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID versionId() {
        return versionId;
    }

    public UUID userId() {
        return userId;
    }

    public String voteType() {
        return voteType;
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

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public BeerVersion getVersion() {
        return version;
    }

    public void setVersion(BeerVersion version) {
        this.version = version;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getVoteType() {
        return voteType;
    }

    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionVote that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
