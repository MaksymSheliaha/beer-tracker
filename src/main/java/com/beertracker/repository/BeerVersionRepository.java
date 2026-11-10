package com.beertracker.repository;

import com.beertracker.model.BeerVersion;
import com.beertracker.model.VersionVote;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class BeerVersionRepository {

    private final Map<UUID, BeerVersion> versionsById = new ConcurrentHashMap<>();
    private final Map<String, VersionVote> votesByUserAndVersion = new ConcurrentHashMap<>();
    private final Map<UUID, Object> versionLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Object> beerDraftLocks = new ConcurrentHashMap<>();

    public Object getLockForVersion(UUID versionId) {
        return versionLocks.computeIfAbsent(versionId, k -> new Object());
    }

    public Object getLockForBeerDraft(UUID beerId) {
        return beerDraftLocks.computeIfAbsent(beerId, k -> new Object());
    }

    public BeerVersion save(BeerVersion version) {
        versionsById.put(version.id(), version);
        return version;
    }

    public Optional<BeerVersion> findById(UUID versionId) {
        return Optional.ofNullable(versionsById.get(versionId));
    }

    public Optional<BeerVersion> findActiveUnverifiedByBeerId(UUID beerId) {
        return versionsById.values().stream()
                .filter(v -> v.beerId().equals(beerId) && "UNVERIFIED".equals(v.status()))
                .findFirst();
    }

    public List<BeerVersion> findByBeerId(UUID beerId) {
        return versionsById.values().stream()
                .filter(v -> v.beerId().equals(beerId))
                .sorted(Comparator.comparing(BeerVersion::createdAt))
                .toList();
    }

    public List<BeerVersion> findByStatus(String status) {
        return versionsById.values().stream()
                .filter(v -> status == null || status.isBlank() || v.status().equalsIgnoreCase(status.trim()))
                .sorted(Comparator.comparing(BeerVersion::createdAt))
                .toList();
    }

    public List<BeerVersion> findAll() {
        return new ArrayList<>(versionsById.values());
    }

    /**
     * Enforces the unique index (user_id, version_id) atomically (REQ-NF-009).
     * Returns true if the vote was inserted, or false if a vote already exists for (userId, versionId).
     */
    public boolean insertVoteIfAbsent(VersionVote vote) {
        String compositeKey = vote.userId() + "::" + vote.versionId();
        return votesByUserAndVersion.putIfAbsent(compositeKey, vote) == null;
    }
}
