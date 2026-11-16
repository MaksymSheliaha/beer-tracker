package com.beertracker.repository;

import com.beertracker.model.VersionVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link VersionVote} records protected by a unique
 * {@code (user_id, version_id)} database constraint ({@code REQ-F-004}, {@code REQ-NF-009}).
 */
@Repository
public interface VersionVoteRepository extends JpaRepository<VersionVote, UUID> {

    /**
     * Checks whether the specified user has already cast a vote on the given version draft.
     *
     * @param userId    voter user UUID
     * @param versionId version draft UUID
     * @return {@code true} if a vote already exists
     */
    boolean existsByUserIdAndVersionId(UUID userId, UUID versionId);
}
