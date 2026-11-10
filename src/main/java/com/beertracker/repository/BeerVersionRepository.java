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

@Repository
public interface BeerVersionRepository extends JpaRepository<BeerVersion, UUID> {

    Map<UUID, Object> VERSION_LOCKS = new ConcurrentHashMap<>();
    Map<UUID, Object> BEER_DRAFT_LOCKS = new ConcurrentHashMap<>();

    List<BeerVersion> findByBeerIdOrderByCreatedAtAsc(UUID beerId);

    Optional<BeerVersion> findFirstByBeerIdAndStatus(UUID beerId, String status);

    default Object getLockForVersion(UUID versionId) {
        return VERSION_LOCKS.computeIfAbsent(versionId, k -> new Object());
    }

    default Object getLockForBeerDraft(UUID beerId) {
        return BEER_DRAFT_LOCKS.computeIfAbsent(beerId, k -> new Object());
    }

    default Optional<BeerVersion> findActiveUnverifiedByBeerId(UUID beerId) {
        return findFirstByBeerIdAndStatus(beerId, "UNVERIFIED");
    }

    default List<BeerVersion> findByBeerId(UUID beerId) {
        return findByBeerIdOrderByCreatedAtAsc(beerId);
    }

    default List<BeerVersion> findByStatus(String status) {
        return findAll().stream()
                .filter(v -> status == null || status.isBlank() || v.status().equalsIgnoreCase(status.trim()))
                .sorted(Comparator.comparing(BeerVersion::createdAt))
                .toList();
    }
}
