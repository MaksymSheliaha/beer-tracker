package com.beertracker.repository;

import com.beertracker.model.ConsumptionEntry;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class ConsumptionRepository {

    private final Map<UUID, ConsumptionEntry> entriesById = new ConcurrentHashMap<>();
    private final List<ConsumptionEntry> appendOnlyLog = new CopyOnWriteArrayList<>();

    public ConsumptionEntry append(ConsumptionEntry entry) {
        entriesById.put(entry.id(), entry);
        appendOnlyLog.add(entry);
        return entry;
    }

    public Optional<ConsumptionEntry> findById(UUID id) {
        return Optional.ofNullable(entriesById.get(id));
    }

    public List<ConsumptionEntry> findAll() {
        return List.copyOf(appendOnlyLog);
    }

    public boolean hasUserConsumedBeer(UUID userId, UUID beerId) {
        return appendOnlyLog.stream()
                .anyMatch(entry -> userId.equals(entry.userId()) && beerId.equals(entry.beerId()));
    }

    public List<ConsumptionEntry> findByUserId(UUID userId) {
        return appendOnlyLog.stream()
                .filter(entry -> userId.equals(entry.userId()))
                .toList();
    }

    public synchronized void updateBeerNameForBeerId(UUID beerId, String newBeerName) {
        for (int i = 0; i < appendOnlyLog.size(); i++) {
            ConsumptionEntry current = appendOnlyLog.get(i);
            if (beerId.equals(current.beerId())) {
                ConsumptionEntry updated = new ConsumptionEntry(
                        current.id(),
                        current.userId(),
                        current.beerId(),
                        current.packagingId(),
                        current.quantity(),
                        current.totalVolumeMl(),
                        current.consumedAt(),
                        current.createdAt(),
                        current.wishlistAutoremoved(),
                        current.collectionAdded(),
                        newBeerName,
                        current.packagingVolumeMl()
                );
                appendOnlyLog.set(i, updated);
                entriesById.put(updated.id(), updated);
            }
        }
    }

    public synchronized int migrateConsumptions(
            UUID sourceBeerId,
            UUID targetBeerId,
            String targetBeerName,
            Map<UUID, UUID> packagingIdMapping
    ) {
        int migratedCount = 0;
        for (int i = 0; i < appendOnlyLog.size(); i++) {
            ConsumptionEntry current = appendOnlyLog.get(i);
            if (sourceBeerId.equals(current.beerId())) {
                UUID mappedPkgId = packagingIdMapping.getOrDefault(current.packagingId(), current.packagingId());
                ConsumptionEntry migrated = new ConsumptionEntry(
                        current.id(),
                        current.userId(),
                        targetBeerId,
                        mappedPkgId,
                        current.quantity(),
                        current.totalVolumeMl(),
                        current.consumedAt(),
                        current.createdAt(),
                        current.wishlistAutoremoved(),
                        current.collectionAdded(),
                        targetBeerName,
                        current.packagingVolumeMl()
                );
                appendOnlyLog.set(i, migrated);
                entriesById.put(migrated.id(), migrated);
                migratedCount++;
            }
        }
        return migratedCount;
    }
}
