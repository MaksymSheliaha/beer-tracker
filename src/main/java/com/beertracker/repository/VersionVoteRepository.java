package com.beertracker.repository;

import com.beertracker.model.VersionVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VersionVoteRepository extends JpaRepository<VersionVote, UUID> {

    boolean existsByUserIdAndVersionId(UUID userId, UUID versionId);
}
