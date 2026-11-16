package com.beertracker.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Resets all H2 JPA tables on Spring application context startup so each application boot
 * or test context starts from a deterministic clean database state.
 */
@Component
public class DatabaseStateInitializer {

    private final VersionVoteRepository versionVoteRepository;
    private final BeerVersionRepository beerVersionRepository;
    private final ConsumptionRepository consumptionRepository;
    private final ReviewRepository reviewRepository;
    private final WishlistRepository wishlistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BeerRepository beerRepository;
    private final UserRepository userRepository;

    /**
     * Constructs the state initializer with all domain JPA repositories.
     *
     * @param versionVoteRepository  repository for version votes
     * @param beerVersionRepository  repository for beer version drafts
     * @param consumptionRepository  repository for consumption entries
     * @param reviewRepository       repository for reviews
     * @param wishlistRepository     repository for wishlist items
     * @param refreshTokenRepository repository for refresh tokens
     * @param beerRepository         repository for catalog beers
     * @param userRepository         repository for users
     */
    public DatabaseStateInitializer(
            VersionVoteRepository versionVoteRepository,
            BeerVersionRepository beerVersionRepository,
            ConsumptionRepository consumptionRepository,
            ReviewRepository reviewRepository,
            WishlistRepository wishlistRepository,
            RefreshTokenRepository refreshTokenRepository,
            BeerRepository beerRepository,
            UserRepository userRepository
    ) {
        this.versionVoteRepository = versionVoteRepository;
        this.beerVersionRepository = beerVersionRepository;
        this.consumptionRepository = consumptionRepository;
        this.reviewRepository = reviewRepository;
        this.wishlistRepository = wishlistRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.beerRepository = beerRepository;
        this.userRepository = userRepository;
    }

    /**
     * Clears all tables in dependency-safe order upon context initialization.
     */
    @PostConstruct
    public void resetDatabaseOnContextStartup() {
        versionVoteRepository.deleteAllInBatch();
        beerVersionRepository.deleteAllInBatch();
        consumptionRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        wishlistRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        beerRepository.deleteAll();
        userRepository.deleteAllInBatch();
    }
}
