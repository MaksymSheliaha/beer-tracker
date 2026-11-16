package com.beertracker.repository;

import com.beertracker.model.BeerVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Data JPA repository for {@link BeerVersion} drafts and per-resource concurrency locks
 * ({@code REQ-F-003}, {@code REQ-NF-009}).
 */
@Repository
public interface BeerVersionRepository extends JpaRepository<BeerVersion, UUID> {

    /**
     * Per-version monitor locks used to serialize concurrent vote updates.
     */
    Map<UUID, Object> VERSION_LOCKS = new ConcurrentHashMap<>();

    /**
     * Per-beer monitor locks used to prevent concurrent unverified draft forks.
     */
    Map<UUID, Object> BEER_DRAFT_LOCKS = new ConcurrentHashMap<>();

    /**
     * Returns all version drafts for a beer sorted chronologically by creation time.
     *
     * @param beerId target beer UUID
     * @return ordered list of version drafts
     */
    List<BeerVersion> findByBeerIdOrderByCreatedAtAsc(UUID beerId);

    /**
     * Finds the first version draft for a beer with the given status.
     *
     * @param beerId target beer UUID
     * @param status target lifecycle status
     * @return matching version draft if present
     */
    Optional<BeerVersion> findFirstByBeerIdAndStatus(UUID beerId, String status);

    /**
     * Returns the monitor lock associated with a specific {@link BeerVersion}.
     *
     * @param versionId version UUID
     * @return synchronization monitor object
     */
    default Object getLockForVersion(UUID versionId) {
        return VERSION_LOCKS.computeIfAbsent(versionId, k -> new Object());
    }

    /**
     * Returns the monitor lock guarding draft creation for a specific {@code Beer}.
     *
     * @param beerId beer UUID
     * @return synchronization monitor object
     */
    default Object getLockForBeerDraft(UUID beerId) {
        return BEER_DRAFT_LOCKS.computeIfAbsent(beerId, k -> new Object());
    }

    /**
     * Finds an active {@code UNVERIFIED} draft for the specified beer ({@code AC-003.2}).
     *
     * @param beerId beer UUID
     * @return active unverified draft if one exists
     */
    default Optional<BeerVersion> findActiveUnverifiedByBeerId(UUID beerId) {
        return findFirstByBeerIdAndStatus(beerId, "UNVERIFIED");
    }

    /**
     * Alias for {@link #findByBeerIdOrderByCreatedAtAsc(UUID)}.
     *
     * @param beerId beer UUID
     * @return ordered version history for the beer
     */
    default List<BeerVersion> findByBeerId(UUID beerId) {
        return findByBeerIdOrderByCreatedAtAsc(beerId);
    }

    /**
     * Filters version drafts by status (or returns all when blank) sorted by creation time.
     *
     * @param status optional status filter
     * @return sorted matching version drafts
     */
    default List<BeerVersion> findByStatus(String status) {
        return findAll().stream()
                .filter(v -> status == null || status.isBlank() || v.status().equalsIgnoreCase(status.trim()))
                .sorted(Comparator.comparing(BeerVersion::createdAt))
                .toList();
    }
}
