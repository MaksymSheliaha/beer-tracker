package com.beertracker.model;

import com.beertracker.dto.BeerVersionCreateRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * JPA entity representing a user-proposed wiki draft version of a catalog {@link Beer}
 * awaiting community voting consensus ({@code N=5}) or moderator decision
 * ({@code REQ-F-003}, {@code REQ-F-004}, {@code REQ-F-005}).
 */
@Entity
@Table(name = "beer_versions")
public class BeerVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "beer_id", nullable = false)
    private UUID beerId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Convert(converter = BeerVersionPayloadConverter.class)
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private BeerVersionCreateRequest payload;

    @Column(name = "confirm_votes", nullable = false)
    private int confirmVotes;

    @Column(name = "reject_votes", nullable = false)
    private int rejectVotes;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VersionVote> votes = new ArrayList<>();

    protected BeerVersion() {
    }

    /**
     * Constructs a {@link BeerVersion} entity with vote counters and lifecycle status.
     *
     * @param id           version UUID
     * @param beerId       target catalog beer UUID
     * @param authorId     author user UUID
     * @param payload      proposed attribute changes and/or new packagings
     * @param confirmVotes accumulated {@code CONFIRM} vote count
     * @param rejectVotes  accumulated {@code REJECT} vote count
     * @param status       lifecycle status ({@code UNVERIFIED}, {@code VERIFIED}, {@code REJECTED})
     * @param createdAt    UTC creation timestamp
     */
    public BeerVersion(
            UUID id,
            UUID beerId,
            UUID authorId,
            BeerVersionCreateRequest payload,
            int confirmVotes,
            int rejectVotes,
            String status,
            Instant createdAt
    ) {
        this.id = id;
        this.beerId = beerId;
        this.authorId = authorId;
        this.payload = payload;
        this.confirmVotes = confirmVotes;
        this.rejectVotes = rejectVotes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID beerId() {
        return beerId;
    }

    public UUID authorId() {
        return authorId;
    }

    public BeerVersionCreateRequest payload() {
        return payload;
    }

    public int confirmVotes() {
        return confirmVotes;
    }

    public int rejectVotes() {
        return rejectVotes;
    }

    public String status() {
        return status;
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

    public UUID getBeerId() {
        return beerId;
    }

    public void setBeerId(UUID beerId) {
        this.beerId = beerId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public void setAuthorId(UUID authorId) {
        this.authorId = authorId;
    }

    public BeerVersionCreateRequest getPayload() {
        return payload;
    }

    public void setPayload(BeerVersionCreateRequest payload) {
        this.payload = payload;
    }

    public int getConfirmVotes() {
        return confirmVotes;
    }

    public void setConfirmVotes(int confirmVotes) {
        this.confirmVotes = confirmVotes;
    }

    public int getRejectVotes() {
        return rejectVotes;
    }

    public void setRejectVotes(int rejectVotes) {
        this.rejectVotes = rejectVotes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<VersionVote> getVotes() {
        return votes;
    }

    public void setVotes(List<VersionVote> votes) {
        this.votes = votes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BeerVersion that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
