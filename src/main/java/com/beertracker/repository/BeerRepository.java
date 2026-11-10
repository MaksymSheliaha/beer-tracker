package com.beertracker.repository;

import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.validation.DomainValidationPredicates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface BeerRepository extends JpaRepository<Beer, UUID> {

    Set<String> HIDDEN_CATALOG_STATUSES = Set.of("PLACEHOLDER", "MERGED", "REJECTED", "DELETED");

    List<Beer> findByStatus(String status);

    default Optional<Beer> findByBreweryAndName(String brewery, String name) {
        String targetKey = normalizeBreweryAndName(brewery, name);
        return findAllVisibleInCatalog().stream()
                .filter(b -> normalizeBreweryAndName(b.brewery(), b.name()).equals(targetKey))
                .findFirst();
    }

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

    default List<Beer> findAllVisibleInCatalog() {
        return findAll().stream()
                .filter(b -> !HIDDEN_CATALOG_STATUSES.contains(b.status()))
                .toList();
    }

    default List<Beer> findAllVisibleByAbvRange(Double minAbv, Double maxAbv) {
        var abvPredicate = DomainValidationPredicates.matchesAbvRange(minAbv, maxAbv);
        return findAll().stream()
                .filter(b -> !HIDDEN_CATALOG_STATUSES.contains(b.status()))
                .filter(b -> abvPredicate.test(b.abv()))
                .toList();
    }

    default boolean deletePermanently(UUID id) {
        if (existsById(id)) {
            deleteById(id);
            flush();
            return true;
        }
        return false;
    }

    static String normalizeBreweryAndName(String brewery, String name) {
        String normBrewery = brewery == null ? "" : brewery.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normName = name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normBrewery + "::" + normName;
    }
}
