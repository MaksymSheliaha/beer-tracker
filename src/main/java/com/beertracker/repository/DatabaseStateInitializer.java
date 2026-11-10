package com.beertracker.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

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
