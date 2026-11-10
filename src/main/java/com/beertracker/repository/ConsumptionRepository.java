package com.beertracker.repository;

import com.beertracker.model.ConsumptionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface ConsumptionRepository extends JpaRepository<ConsumptionEntry, UUID> {

    boolean existsByUserIdAndBeerId(UUID userId, UUID beerId);

    List<ConsumptionEntry> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<ConsumptionEntry> findByBeerId(UUID beerId);

    default ConsumptionEntry append(ConsumptionEntry entry) {
        return saveAndFlush(entry);
    }

    default boolean hasUserConsumedBeer(UUID userId, UUID beerId) {
        return existsByUserIdAndBeerId(userId, beerId);
    }

    default List<ConsumptionEntry> findByUserId(UUID userId) {
        return findByUserIdOrderByCreatedAtAsc(userId);
    }

    default void updateBeerNameForBeerId(UUID beerId, String newBeerName) {
        List<ConsumptionEntry> entries = findByBeerId(beerId);
        for (ConsumptionEntry current : entries) {
            current.setBeerName(newBeerName);
        }
        saveAllAndFlush(entries);
    }

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
