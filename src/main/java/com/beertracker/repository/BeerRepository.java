package com.beertracker.repository;

import com.beertracker.model.Beer;
import com.beertracker.validation.DomainValidationPredicates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data JPA repository for canonical {@link Beer} catalog entities ({@code REQ-F-001}, {@code REQ-F-002}).
 */
@Repository
public interface BeerRepository extends JpaRepository<Beer, UUID> {

    /**
     * Statuses excluded from public catalog listings and deduplication indices.
     */
    Set<String> HIDDEN_CATALOG_STATUSES = Set.of("PLACEHOLDER", "MERGED", "REJECTED", "DELETED");

    /**
     * Statuses indicating a beer record has been soft-deleted, purged, or merged into a target beer.
     */
    Set<String> INACTIVE_ENTITY_STATUSES = Set.of("PLACEHOLDER", "MERGED", "DELETED");

    /**
     * Finds all beers matching the specified lifecycle status.
     *
     * @param status beer lifecycle status ({@code UNVERIFIED}, {@code VERIFIED}, {@code REJECTED}, etc.)
     * @return matching beer entities
     */
    List<Beer> findByStatus(String status);

    /**
     * Retrieves a beer by UUID only if it has not been soft-deleted, merged, or permanently removed.
     *
     * @param id beer UUID
     * @return active {@link Beer} if accessible, or empty if absent/inactive
     */
    default Optional<Beer> findActiveById(UUID id) {
        return findById(id).filter(b -> !INACTIVE_ENTITY_STATUSES.contains(b.status()));
    }

    /**
     * Looks up a visible catalog beer matching the normalized {@code (brewery, name)} pair
     * (case-insensitive, collapsed whitespace) for preventive deduplication ({@code AC-002.1}).
     *
     * @param brewery brewery name
     * @param name    beer name
     * @return matching visible beer if already registered
     */
    default Optional<Beer> findByBreweryAndName(String brewery, String name) {
        String targetKey = normalizeBreweryAndName(brewery, name);
        return findAllVisibleInCatalog().stream()
                .filter(b -> normalizeBreweryAndName(b.brewery(), b.name()).equals(targetKey))
                .findFirst();
    }

    /**
     * Looks up a visible catalog beer owning a packaging with the given barcode ({@code AC-002.2}).
     *
     * @param barcode packaging barcode string
     * @return matching visible beer if the barcode is already assigned
     */
    default Optional<Beer> findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        String trimmed = barcode.trim();
        return findAllVisibleInCatalog().stream()
                .filter(b -> b.packagings() != null && b.packagings().stream()
                        .anyMatch(pkg -> pkg.barcode() != null && pkg.barcode().trim().equals(trimmed)))
                .findFirst();
    }

    /**
     * Returns all catalog beers excluding hidden statuses ({@code PLACEHOLDER}, {@code MERGED},
     * {@code REJECTED}, {@code DELETED}).
     *
     * @return visible catalog beers
     */
    default List<Beer> findAllVisibleInCatalog() {
        return findAll().stream()
                .filter(b -> !HIDDEN_CATALOG_STATUSES.contains(b.status()))
                .toList();
    }

    /**
     * Returns all visible catalog beers whose ABV lies within {@code [minAbv, maxAbv]} ({@code REQ-F-015}).
     *
     * @param minAbv optional inclusive minimum ABV percentage
     * @param maxAbv optional inclusive maximum ABV percentage
     * @return matching visible beers
     */
    default List<Beer> findAllVisibleByAbvRange(Double minAbv, Double maxAbv) {
        var abvPredicate = DomainValidationPredicates.matchesAbvRange(minAbv, maxAbv);
        return findAll().stream()
                .filter(b -> !HIDDEN_CATALOG_STATUSES.contains(b.status()))
                .filter(b -> abvPredicate.test(b.abv()))
                .toList();
    }

    /**
     * Permanently deletes a beer entity by UUID when present and flushes immediately.
     *
     * @param id beer UUID
     * @return {@code true} if the beer existed and was deleted, {@code false} otherwise
     */
    default boolean deletePermanently(UUID id) {
        if (existsById(id)) {
            deleteById(id);
            flush();
            return true;
        }
        return false;
    }

    /**
     * Produces a canonical deduplication key from brewery and beer names.
     *
     * @param brewery raw brewery string
     * @param name    raw beer name string
     * @return normalized {@code brewery::name} key
     */
    static String normalizeBreweryAndName(String brewery, String name) {
        String normBrewery = brewery == null
                ? ""
                : brewery.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normName = name == null
                ? ""
                : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normBrewery + "::" + normName;
    }
}
