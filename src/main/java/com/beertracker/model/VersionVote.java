package com.beertracker.model;

import java.time.Instant;
import java.util.UUID;

public record VersionVote(
        UUID id,
        UUID versionId,
        UUID userId,
        String voteType,
        Instant createdAt
) {
}
