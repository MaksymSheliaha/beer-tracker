package com.beertracker.repository;

import com.beertracker.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, UUID> {

    Optional<WishlistItem> findByUserIdAndBeerId(UUID userId, UUID beerId);

    List<WishlistItem> findByUserIdOrderByAddedAtDesc(UUID userId);

    List<WishlistItem> findByBeerId(UUID beerId);

    default Optional<WishlistItem> findByUserAndBeer(UUID userId, UUID beerId) {
        return findByUserIdAndBeerId(userId, beerId);
    }

    default List<WishlistItem> findByUser(UUID userId) {
        return findByUserIdOrderByAddedAtDesc(userId);
    }

    default boolean removeByUserAndBeer(UUID userId, UUID beerId) {
        Optional<WishlistItem> existing = findByUserIdAndBeerId(userId, beerId);
        if (existing.isPresent()) {
            delete(existing.get());
            flush();
            return true;
        }
        return false;
    }

    default void removeAllByBeer(UUID beerId) {
        List<WishlistItem> items = findByBeerId(beerId);
        if (!items.isEmpty()) {
            deleteAll(items);
            flush();
        }
    }
}
