package com.beertracker.repository;

import com.beertracker.model.ConsumptionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spring Data JPA repository for the append-only {@link ConsumptionEntry} log
 * ({@code REQ-F-006}, {@code REQ-F-007}, {@code REQ-F-010}, {@code REQ-F-011}).
 *
 * <p>Direct user mutations are forbidden; only internal catalog integrity actions
 * ({@code Placeholder Beer} renaming and {@code Merge} foreign-key re-linking) update references.</p>
 */
@Repository
public interface ConsumptionRepository extends JpaRepository<ConsumptionEntry, UUID> {

    /**
     * Checks whether the given user has recorded at least one consumption entry for a beer.
     *
     * @param userId user UUID
     * @param beerId beer UUID
     * @return {@code true} if at least one consumption record exists
     */
    boolean existsByUserIdAndBeerId(UUID userId, UUID beerId);

    /**
     * Lists all consumption entries owned by a user ordered chronologically by creation time.
     *
     * @param userId owner user UUID
     * @return ordered list of consumption entries
     */
    List<ConsumptionEntry> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Lists all consumption entries referencing a specific beer (used for soft-delete/merge propagation).
     *
     * @param beerId beer UUID
     * @return matching consumption entries
     */
    List<ConsumptionEntry> findByBeerId(UUID beerId);

    /**
     * Appends a new immutable consumption entry and flushes immediately.
     *
     * @param entry consumption entry to persist
     * @return persisted entity
     */
    default ConsumptionEntry append(ConsumptionEntry entry) {
        return saveAndFlush(entry);
    }

    /**
     * Checks whether the user has consumed the given beer before allowing review creation ({@code AC-008.1}).
     *
     * @param userId user UUID
     * @param beerId beer UUID
     * @return {@code true} when at least one consumption entry exists
     */
    default boolean hasUserConsumedBeer(UUID userId, UUID beerId) {
        return existsByUserIdAndBeerId(userId, beerId);
    }

    /**
     * Retrieves all consumption entries belonging to the specified user.
     *
     * @param userId owner user UUID
     * @return chronological list of user entries
     */
    default List<ConsumptionEntry> findByUserId(UUID userId) {
        return findByUserIdOrderByCreatedAtAsc(userId);
    }

    /**
     * Updates cached display names across consumption history when a beer is soft-deleted
     * into {@code Placeholder Beer} ({@code AC-010.2}).
     *
     * @param beerId      soft-deleted beer UUID
     * @param newBeerName placeholder display label
     */
    default void updateBeerNameForBeerId(UUID beerId, String newBeerName) {
        List<ConsumptionEntry> entries = findByBeerId(beerId);
        for (ConsumptionEntry current : entries) {
            current.setBeerName(newBeerName);
        }
        saveAllAndFlush(entries);
    }

    /**
     * Atomically re-links consumption entries from a merged source beer to a target beer
     * and its mapped packagings ({@code AC-011.2}).
     *
     * @param sourceBeerId       merged duplicate beer UUID
     * @param targetBeerId       canonical target beer UUID
     * @param targetBeerName     canonical target beer display name
     * @param packagingIdMapping mapping from source packaging IDs to target packaging IDs
     * @return number of migrated consumption entries
     */
    default int migrateConsumptions(
            UUID sourceBeerId,
            UUID targetBeerId,
            String targetBeerName,
            Map<UUID, UUID> packagingIdMapping
    ) {
        List<ConsumptionEntry> entries = findByBeerId(sourceBeerId);
        for (ConsumptionEntry current : entries) {
            UUID mappedPkgId = packagingIdMapping.getOrDefault(current.packagingId(), current.packagingId());
            current.setBeerId(targetBeerId);
            current.setPackagingId(mappedPkgId);
            current.setBeerName(targetBeerName);
        }
        saveAllAndFlush(entries);
        return entries.size();
    }
}
