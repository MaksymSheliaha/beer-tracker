package com.beertracker.repository;

import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.validation.DomainValidationPredicates;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class BeerRepository {

    private static final Set<String> HIDDEN_CATALOG_STATUSES = Set.of("PLACEHOLDER", "MERGED", "REJECTED", "DELETED");

    private final Map<UUID, Beer> beersById = new ConcurrentHashMap<>();
    private final Map<String, UUID> beerIdByNormalizedName = new ConcurrentHashMap<>();
    private final Map<String, UUID> beerIdByBarcode = new ConcurrentHashMap<>();

    public Beer save(Beer beer) {
        Beer previous = beersById.get(beer.id());
        if (previous != null) {
            removeIndexes(previous);
        }
        beersById.put(beer.id(), beer);
        if (!HIDDEN_CATALOG_STATUSES.contains(beer.status())) {
            String normalizedKey = normalizeBreweryAndName(beer.brewery(), beer.name());
            beerIdByNormalizedName.put(normalizedKey, beer.id());
            for (BeerPackaging packaging : beer.packagings()) {
                if (packaging.barcode() != null && !packaging.barcode().isBlank()) {
                    beerIdByBarcode.put(packaging.barcode().trim(), beer.id());
                }
            }
        }
        return beer;
    }

    public Optional<Beer> findById(UUID id) {
        return Optional.ofNullable(beersById.get(id));
    }

    public Optional<Beer> findByBreweryAndName(String brewery, String name) {
        UUID id = beerIdByNormalizedName.get(normalizeBreweryAndName(brewery, name));
        return id == null ? Optional.empty() : Optional.ofNullable(beersById.get(id));
    }

    public Optional<Beer> findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        UUID id = beerIdByBarcode.get(barcode.trim());
        return id == null ? Optional.empty() : Optional.ofNullable(beersById.get(id));
    }

    public List<Beer> findAll() {
        return new ArrayList<>(beersById.values());
    }

    public List<Beer> findAllVisibleInCatalog() {
        return beersById.values().stream()
                .filter(b -> !HIDDEN_CATALOG_STATUSES.contains(b.status()))
                .toList();
    }

    public List<Beer> findAllVisibleByAbvRange(Double minAbv, Double maxAbv) {
        var abvPredicate = DomainValidationPredicates.matchesAbvRange(minAbv, maxAbv);
        return beersById.values().stream()
                .filter(b -> !HIDDEN_CATALOG_STATUSES.contains(b.status()))
                .filter(b -> abvPredicate.test(b.abv()))
                .toList();
    }

    public List<Beer> findByStatus(String status) {
        return beersById.values().stream()
                .filter(b -> status.equals(b.status()))
                .toList();
    }

    public boolean deletePermanently(UUID id) {
        Beer removed = beersById.remove(id);
        if (removed != null) {
            removeIndexes(removed);
            return true;
        }
        return false;
    }

    private void removeIndexes(Beer beer) {
        beerIdByNormalizedName.remove(normalizeBreweryAndName(beer.brewery(), beer.name()), beer.id());
        if (beer.packagings() != null) {
            for (BeerPackaging packaging : beer.packagings()) {
                if (packaging.barcode() != null && !packaging.barcode().isBlank()) {
                    beerIdByBarcode.remove(packaging.barcode().trim(), beer.id());
                }
            }
        }
    }

    public static String normalizeBreweryAndName(String brewery, String name) {
        String normBrewery = brewery == null ? "" : brewery.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normName = name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normBrewery + "::" + normName;
    }
}
