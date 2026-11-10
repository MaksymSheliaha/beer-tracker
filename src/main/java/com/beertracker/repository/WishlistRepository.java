package com.beertracker.repository;

import com.beertracker.model.WishlistItem;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WishlistRepository {

    private final Map<String, WishlistItem> itemsByUserAndBeer = new ConcurrentHashMap<>();

    private static String key(UUID userId, UUID beerId) {
        return userId + "::" + beerId;
    }

    public WishlistItem save(WishlistItem item) {
        itemsByUserAndBeer.put(key(item.userId(), item.beerId()), item);
        return item;
    }

    public Optional<WishlistItem> findByUserAndBeer(UUID userId, UUID beerId) {
        return Optional.ofNullable(itemsByUserAndBeer.get(key(userId, beerId)));
    }

    public List<WishlistItem> findByUser(UUID userId) {
        return itemsByUserAndBeer.values().stream()
                .filter(item -> userId.equals(item.userId()))
                .sorted(Comparator.comparing(WishlistItem::addedAt).reversed())
                .toList();
    }

    public boolean removeByUserAndBeer(UUID userId, UUID beerId) {
        return itemsByUserAndBeer.remove(key(userId, beerId)) != null;
    }

    public void removeAllByBeer(UUID beerId) {
        itemsByUserAndBeer.values().removeIf(item -> beerId.equals(item.beerId()));
    }
}
