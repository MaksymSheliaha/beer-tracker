package com.beertracker.repository;

import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class BeerRepository {

    private final Map<UUID, Beer> beersById = new ConcurrentHashMap<>();
    private final Map<String, UUID> beerIdByNormalizedName = new ConcurrentHashMap<>();
    private final Map<String, UUID> beerIdByBarcode = new ConcurrentHashMap<>();

    public Beer save(Beer beer) {
        String normalizedKey = normalizeBreweryAndName(beer.brewery(), beer.name());
        beersById.put(beer.id(), beer);
        beerIdByNormalizedName.put(normalizedKey, beer.id());
        for (BeerPackaging packaging : beer.packagings()) {
            if (packaging.barcode() != null && !packaging.barcode().isBlank()) {
                beerIdByBarcode.put(packaging.barcode().trim(), beer.id());
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

    public static String normalizeBreweryAndName(String brewery, String name) {
        String normBrewery = brewery == null ? "" : brewery.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normName = name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normBrewery + "::" + normName;
    }
}
