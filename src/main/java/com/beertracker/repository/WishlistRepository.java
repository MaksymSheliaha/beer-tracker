package com.beertracker.repository;

import com.beertracker.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for personal {@link WishlistItem} entries ({@code REQ-F-009}, {@code REQ-NF-005}).
 */
@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, UUID> {

    /**
     * Finds a wishlist entry for a specific {@code (user, beer)} pair.
     *
     * @param userId owner user UUID
     * @param beerId catalog beer UUID
     * @return matching wishlist item if saved
     */
    Optional<WishlistItem> findByUserIdAndBeerId(UUID userId, UUID beerId);

    /**
     * Lists a user's wishlist items ordered from newest to oldest.
     *
     * @param userId owner user UUID
     * @return ordered wishlist items
     */
    List<WishlistItem> findByUserIdOrderByAddedAtDesc(UUID userId);

    /**
     * Finds all wishlist items referencing a specific beer across all users.
     *
     * @param beerId catalog beer UUID
     * @return matching wishlist items
     */
    List<WishlistItem> findByBeerId(UUID beerId);

    /**
     * Alias for {@link #findByUserIdAndBeerId(UUID, UUID)}.
     *
     * @param userId owner user UUID
     * @param beerId catalog beer UUID
     * @return matching wishlist item if present
     */
    default Optional<WishlistItem> findByUserAndBeer(UUID userId, UUID beerId) {
        return findByUserIdAndBeerId(userId, beerId);
    }

    /**
     * Alias for {@link #findByUserIdOrderByAddedAtDesc(UUID)}.
     *
     * @param userId owner user UUID
     * @return user's ordered wishlist
     */
    default List<WishlistItem> findByUser(UUID userId) {
        return findByUserIdOrderByAddedAtDesc(userId);
    }

    /**
     * Deletes a specific beer from the user's wishlist if present.
     *
     * @param userId owner user UUID
     * @param beerId catalog beer UUID
     * @return {@code true} if an entry was removed, {@code false} otherwise
     */
    default boolean removeByUserAndBeer(UUID userId, UUID beerId) {
        Optional<WishlistItem> existing = findByUserIdAndBeerId(userId, beerId);
        if (existing.isPresent()) {
            delete(existing.get());
            flush();
            return true;
        }
        return false;
    }

    /**
     * Removes all wishlist references to a beer (used during soft delete and merge cleanup).
     *
     * @param beerId target catalog beer UUID
     */
    default void removeAllByBeer(UUID beerId) {
        List<WishlistItem> items = findByBeerId(beerId);
        if (!items.isEmpty()) {
            deleteAll(items);
            flush();
        }
    }
}
