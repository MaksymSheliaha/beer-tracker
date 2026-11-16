package com.beertracker.service;

import com.beertracker.dto.BeerPackagingCreateRequest;
import com.beertracker.dto.BeerVersionCreateRequest;
import com.beertracker.dto.BeerVersionPagedResponse;
import com.beertracker.dto.BeerVersionResponse;
import com.beertracker.dto.BeerVersionVoteRequest;
import com.beertracker.dto.BeerVersionVoteResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.BeerMapper;
import com.beertracker.mapper.BeerVersionMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerVersion;
import com.beertracker.model.VersionVote;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.BeerVersionRepository;
import com.beertracker.repository.VersionVoteRepository;
import com.beertracker.validation.DomainValidationPredicates;
import com.beertracker.validation.PaginationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service coordinating wiki-style {@link BeerVersion} proposals, fork protection,
 * and race-free community consensus voting ({@code N=5})
 * ({@code REQ-F-003}, {@code REQ-F-004}, {@code REQ-F-005}, {@code REQ-NF-009}).
 */
@Service
public class BeerVersionService {

    /**
     * Fixed consensus threshold of identical votes required for automatic verification or rejection.
     */
    public static final int CONSENSUS_THRESHOLD = 5;

    private static final Logger LOG = LoggerFactory.getLogger(BeerVersionService.class);

    private final BeerVersionRepository beerVersionRepository;
    private final VersionVoteRepository versionVoteRepository;
    private final BeerRepository beerRepository;
    private final BeerVersionMapper beerVersionMapper;
    private final BeerMapper beerMapper;
    private final ReadCommittedTxExecutor txExecutor;

    /**
     * Constructs the version management service.
     *
     * @param beerVersionRepository repository for beer versions and monitor locks
     * @param versionVoteRepository repository for unique user votes
     * @param beerRepository        repository for catalog beers
     * @param beerVersionMapper     mapper for version DTOs
     * @param beerMapper            mapper applying verified payloads to catalog beers
     * @param txExecutor            ACID {@code READ_COMMITTED} transaction executor
     */
    public BeerVersionService(
            BeerVersionRepository beerVersionRepository,
            VersionVoteRepository versionVoteRepository,
            BeerRepository beerRepository,
            BeerVersionMapper beerVersionMapper,
            BeerMapper beerMapper,
            ReadCommittedTxExecutor txExecutor
    ) {
        this.beerVersionRepository = beerVersionRepository;
        this.versionVoteRepository = versionVoteRepository;
        this.beerRepository = beerRepository;
        this.beerVersionMapper = beerVersionMapper;
        this.beerMapper = beerMapper;
        this.txExecutor = txExecutor;
    }

    /**
     * Creates a new {@code UNVERIFIED} {@link BeerVersion} draft while guaranteeing at most one
     * active unverified draft per beer ({@code AC-003.1}, {@code AC-003.2}).
     *
     * @param beerId   target catalog beer UUID
     * @param authorId authenticated author UUID
     * @param request  proposed attribute changes and/or packagings
     * @return created {@link BeerVersionResponse}
     * @throws ApiException with {@code 404 NOT_FOUND} if the beer does not exist or is inactive,
     *                      {@code 409 CONFLICT} on concurrent draft or duplicate barcode,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} on domain validation errors
     */
    public BeerVersionResponse createBeerVersion(UUID beerId, UUID authorId, BeerVersionCreateRequest request) {
        synchronized (beerVersionRepository.getLockForBeerDraft(beerId)) {
            return txExecutor.execute(() -> {
                Beer beer = beerRepository.findActiveById(beerId)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "Resource requested at /beers/" + beerId + "/versions was not found",
                                List.of(new ErrorDetail("id", "Requested resource with specified ID was not found"))
                        ));

                validateVersionPayload(beer, request);

                Optional<BeerVersion> existingDraft = beerVersionRepository.findActiveUnverifiedByBeerId(beerId);
                if (existingDraft.isPresent()) {
                    String issue = "Pending draft '%s' must be resolved first".formatted(existingDraft.get().id());
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "CONCURRENT_DRAFT_CONFLICT",
                            "An active unverified version already exists for this beer product",
                            List.of(new ErrorDetail("beer_id", issue))
                    );
                }

                BeerVersion version = beerVersionMapper.toEntity(beerId, authorId, request);
                beerVersionRepository.save(version);

                LOG.info("Beer version draft created",
                        kv("event", "beer_version_created"),
                        kv("version_id", version.id().toString()),
                        kv("beer_id", beerId.toString()),
                        kv("author_id", authorId.toString()),
                        kv("status", "201"));

                return beerVersionMapper.toResponse(version);
            });
        }
    }

    /**
     * Lists all versions proposed for an active catalog beer in chronological order.
     *
     * @param beerId catalog beer UUID
     * @return list of {@link BeerVersionResponse}
     * @throws ApiException with {@code 404 NOT_FOUND} if the beer does not exist or is inactive
     */
    @Transactional(readOnly = true)
    public List<BeerVersionResponse> listBeerVersions(UUID beerId) {
        if (beerRepository.findActiveById(beerId).isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "NOT_FOUND",
                    "Resource requested at /beers/" + beerId + "/versions was not found",
                    List.of(new ErrorDetail("id", "Requested resource with specified ID was not found"))
            );
        }

        return beerVersionRepository.findByBeerId(beerId).stream()
                .map(beerVersionMapper::toResponse)
                .toList();
    }

    /**
     * Lists paginated version drafts filtered by status.
     *
     * @param status optional status filter ({@code UNVERIFIED}, {@code VERIFIED}, {@code REJECTED})
     * @param page   zero-based page index
     * @param size   page size (1..100)
     * @return paginated {@link BeerVersionPagedResponse}
     */
    @Transactional(readOnly = true)
    public BeerVersionPagedResponse listVersionsByStatus(String status, int page, int size) {
        PaginationHelper.validate(page, size);

        List<BeerVersionResponse> filtered = beerVersionRepository.findByStatus(status).stream()
                .map(beerVersionMapper::toResponse)
                .toList();

        return beerVersionMapper.toPagedResponse(filtered, page, size);
    }

    /**
     * Records a community {@code CONFIRM} or {@code REJECT} vote inside a per-version monitor lock
     * and {@code READ_COMMITTED} transaction, triggering automatic status transition at {@code N=5}
     * ({@code AC-004.1}..{@code AC-005.3}, {@code REQ-NF-009}).
     *
     * @param versionId version draft UUID
     * @param voterId   authenticated voter UUID
     * @param request   vote payload ({@code CONFIRM} or {@code REJECT})
     * @return updated counters and lifecycle status in {@link BeerVersionVoteResponse}
     * @throws ApiException with {@code 403 FORBIDDEN} on self-voting,
     *                      {@code 404 NOT_FOUND} if version does not exist,
     *                      {@code 409 CONFLICT} on duplicate vote,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} for invalid vote type
     */
    public BeerVersionVoteResponse vote(UUID versionId, UUID voterId, BeerVersionVoteRequest request) {
        if (request == null
                || request.voteType() == null
                || (!"CONFIRM".equals(request.voteType()) && !"REJECT".equals(request.voteType()))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "vote_type must be CONFIRM or REJECT",
                    List.of(new ErrorDetail("vote_type", "Allowed values are CONFIRM or REJECT"))
            );
        }

        synchronized (beerVersionRepository.getLockForVersion(versionId)) {
            return txExecutor.execute(() -> {
                BeerVersion version = beerVersionRepository.findById(versionId)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "Resource requested at /beer-versions/" + versionId + "/votes was not found",
                                List.of(new ErrorDetail("id", "Requested resource with specified ID was not found"))
                        ));

                if (voterId.equals(version.authorId())) {
                    LOG.warn("Author attempted to vote on own version",
                            kv("event", "security_violation_audit"),
                            kv("violation_type", "SELF_VOTING_FORBIDDEN"),
                            kv("user_id", voterId.toString()),
                            kv("version_id", versionId.toString()),
                            kv("status", "403"));
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            "SELF_VOTING_FORBIDDEN",
                            "Authors are forbidden from voting on their own proposed versions",
                            List.of(new ErrorDetail("user_id", "User is the author of version '" + versionId + "'"))
                    );
                }

                if (versionVoteRepository.existsByUserIdAndVersionId(voterId, versionId)) {
                    String issue = "A vote from the current user has already been recorded for this version";
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "DUPLICATE_VOTE_CONFLICT",
                            "User has already cast a vote for this version",
                            List.of(new ErrorDetail("id", issue))
                    );
                }

                VersionVote vote = new VersionVote(
                        UUID.randomUUID(),
                        versionId,
                        voterId,
                        request.voteType(),
                        Instant.now()
                );
                versionVoteRepository.saveAndFlush(vote);

                int newConfirmVotes = version.confirmVotes() + ("CONFIRM".equals(request.voteType()) ? 1 : 0);
                int newRejectVotes = version.rejectVotes() + ("REJECT".equals(request.voteType()) ? 1 : 0);
                String newStatus = version.status();
                String message = "Vote recorded successfully";

                if ("UNVERIFIED".equals(version.status()) && newConfirmVotes >= CONSENSUS_THRESHOLD) {
                    newStatus = "VERIFIED";
                    message = "Version successfully verified by community consensus (threshold N=5 reached)";
                    applyVerifiedVersionToCatalog(version);

                    LOG.info("Beer version verified by community consensus",
                            kv("event", "version_verification_completed"),
                            kv("version_id", versionId.toString()),
                            kv("beer_id", version.beerId().toString()),
                            kv("previous_status", "UNVERIFIED"),
                            kv("new_status", "VERIFIED"),
                            kv("trigger", "COMMUNITY_CONSENSUS"),
                            kv("confirm_votes", newConfirmVotes),
                            kv("reject_votes", newRejectVotes),
                            kv("status", "200"));
                } else if ("UNVERIFIED".equals(version.status()) && newRejectVotes >= CONSENSUS_THRESHOLD) {
                    newStatus = "REJECTED";
                    message = "Version rejected by community consensus (threshold N=5 reached)";
                    applyRejectedVersionToCatalogIfNewProduct(version.beerId());

                    LOG.info("Beer version rejected by community consensus",
                            kv("event", "version_verification_completed"),
                            kv("version_id", versionId.toString()),
                            kv("beer_id", version.beerId().toString()),
                            kv("previous_status", "UNVERIFIED"),
                            kv("new_status", "REJECTED"),
                            kv("trigger", "COMMUNITY_CONSENSUS"),
                            kv("confirm_votes", newConfirmVotes),
                            kv("reject_votes", newRejectVotes),
                            kv("status", "200"));
                }

                BeerVersion updatedVersion = new BeerVersion(
                        version.id(),
                        version.beerId(),
                        version.authorId(),
                        version.payload(),
                        newConfirmVotes,
                        newRejectVotes,
                        newStatus,
                        version.createdAt()
                );
                beerVersionRepository.save(updatedVersion);

                return new BeerVersionVoteResponse(
                        updatedVersion.id(),
                        updatedVersion.confirmVotes(),
                        updatedVersion.rejectVotes(),
                        updatedVersion.status(),
                        message
                );
            });
        }
    }

    private void applyVerifiedVersionToCatalog(BeerVersion version) {
        beerRepository.findById(version.beerId()).ifPresent(existingBeer -> {
            Beer updatedBeer = beerMapper.mergeWithVersionPayload(existingBeer, version.payload(), "VERIFIED");
            beerRepository.save(updatedBeer);
        });
    }

    private void applyRejectedVersionToCatalogIfNewProduct(UUID beerId) {
        beerRepository.findById(beerId).ifPresent(existingBeer -> {
            if ("UNVERIFIED".equals(existingBeer.status())) {
                Beer rejectedBeer = new Beer(
                        existingBeer.id(),
                        existingBeer.name(),
                        existingBeer.brewery(),
                        existingBeer.style(),
                        existingBeer.abv(),
                        existingBeer.beerType(),
                        existingBeer.description(),
                        existingBeer.country(),
                        existingBeer.imageUrl(),
                        "REJECTED",
                        existingBeer.ratingAvg(),
                        existingBeer.reviewsCount(),
                        existingBeer.packagings(),
                        existingBeer.createdAt()
                );
                beerRepository.save(rejectedBeer);
            }
        });
    }

    private void validateVersionPayload(Beer beer, BeerVersionCreateRequest request) {
        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Version payload is required",
                    List.of(new ErrorDetail("body", "Request body cannot be null"))
            );
        }

        if (request.abv() != null && !DomainValidationPredicates.IS_VALID_ABV.test(request.abv())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "Provided attributes violated domain business rules",
                    List.of(new ErrorDetail("abv", "Provided value must be between 0.0 and 100.0"))
            );
        }

        if (request.beerType() != null && !DomainValidationPredicates.IS_VALID_BEER_TYPE.test(request.beerType())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "Invalid beer_type",
                    List.of(new ErrorDetail("beer_type", "Must be PACKAGED or DRAFT"))
            );
        }

        if (request.packagings() != null) {
            String effectiveBeerType = request.beerType() != null ? request.beerType() : beer.beerType();
            for (int i = 0; i < request.packagings().size(); i++) {
                BeerPackagingCreateRequest pkg = request.packagings().get(i);
                String fieldPrefix = "packagings[" + i + "]";

                if (!DomainValidationPredicates.IS_VALID_PACKAGING_VOLUME.test(pkg.volumeMl())) {
                    String issue = "Volume must be between 50 and 10000 ml";
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "VOLUME_OUT_OF_BOUNDS",
                            "Packaging volume must be strictly between 50 and 10000 ml",
                            List.of(new ErrorDetail(fieldPrefix + ".volume_ml", issue))
                    );
                }

                if (DomainValidationPredicates.IS_DRAFT_PACKAGE_INCOMPATIBLE
                        .test(effectiveBeerType, pkg.packageType())) {
                    String issue = "Incompatible package_type for DRAFT";
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "INVALID_PACKAGE_TYPE_FOR_DRAFT",
                            "All packagings for DRAFT beer must have package_type DRAFT",
                            List.of(new ErrorDetail(fieldPrefix + ".package_type", issue))
                    );
                }

                if (DomainValidationPredicates.IS_PACKAGED_DRAFT_INCOMPATIBLE
                        .test(effectiveBeerType, pkg.packageType())) {
                    String issue = "package_type DRAFT is not allowed for PACKAGED beer";
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "UNPROCESSABLE_ENTITY",
                            "PACKAGED beer cannot have package_type DRAFT",
                            List.of(new ErrorDetail(fieldPrefix + ".package_type", issue))
                    );
                }

                if (DomainValidationPredicates.HAS_BARCODE.test(pkg.barcode())) {
                    if (!DomainValidationPredicates.IS_VALID_BARCODE_FORMAT.test(pkg.barcode())) {
                        String issue = "Barcode must match ^\\d{8,14}$";
                        throw new ApiException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "INVALID_BARCODE_FORMAT",
                                "Barcode must consist of 8 to 14 digits",
                                List.of(new ErrorDetail(fieldPrefix + ".barcode", issue))
                        );
                    }
                    String barcode = pkg.barcode().trim();
                    Optional<Beer> existingByBarcode = beerRepository.findByBarcode(barcode);
                    if (existingByBarcode.isPresent() && !existingByBarcode.get().id().equals(beer.id())) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "DUPLICATE_BARCODE_CONFLICT",
                                "Штрих-код " + barcode + " уже прив'язаний до іншого товару",
                                List.of(new ErrorDetail(fieldPrefix + ".barcode", "Barcode is already assigned"))
                        );
                    }
                }
            }
        }
    }
}
