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
import com.beertracker.model.BeerPackaging;
import com.beertracker.model.BeerVersion;
import com.beertracker.model.VersionVote;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.BeerVersionRepository;
import com.beertracker.repository.VersionVoteRepository;
import com.beertracker.validation.DomainValidationPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class BeerVersionService {

    private static final Logger log = LoggerFactory.getLogger(BeerVersionService.class);
    public static final int CONSENSUS_THRESHOLD = 5;

    private final BeerVersionRepository beerVersionRepository;
    private final VersionVoteRepository versionVoteRepository;
    private final BeerRepository beerRepository;
    private final BeerVersionMapper beerVersionMapper;
    private final BeerMapper beerMapper;

    public BeerVersionService(
            BeerVersionRepository beerVersionRepository,
            VersionVoteRepository versionVoteRepository,
            BeerRepository beerRepository,
            BeerVersionMapper beerVersionMapper,
            BeerMapper beerMapper
    ) {
        this.beerVersionRepository = beerVersionRepository;
        this.versionVoteRepository = versionVoteRepository;
        this.beerRepository = beerRepository;
        this.beerVersionMapper = beerVersionMapper;
        this.beerMapper = beerMapper;
    }

    public BeerVersionResponse createBeerVersion(UUID beerId, UUID authorId, BeerVersionCreateRequest request) {
        Beer beer = beerRepository.findById(beerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Resource requested at /beers/" + beerId + "/versions was not found",
                        List.of(new ErrorDetail("id", "Requested resource with the specified identifier was not found"))
                ));

        validateVersionPayload(beer, request);

        synchronized (beerVersionRepository.getLockForBeerDraft(beerId)) {
            Optional<BeerVersion> existingDraft = beerVersionRepository.findActiveUnverifiedByBeerId(beerId);
            if (existingDraft.isPresent()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CONCURRENT_DRAFT_CONFLICT",
                        "An active unverified version already exists for this beer product",
                        List.of(new ErrorDetail(
                                "beer_id",
                                "Pending draft '%s' must be resolved first".formatted(existingDraft.get().id())
                        ))
                );
            }

            BeerVersion version = beerVersionMapper.toEntity(beerId, authorId, request);
            beerVersionRepository.save(version);

            log.info("Beer version draft created",
                    kv("event", "beer_version_created"),
                    kv("version_id", version.id().toString()),
                    kv("beer_id", beerId.toString()),
                    kv("author_id", authorId.toString()),
                    kv("status", "201"));

            return beerVersionMapper.toResponse(version);
        }
    }

    public List<BeerVersionResponse> listBeerVersions(UUID beerId) {
        if (beerRepository.findById(beerId).isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "NOT_FOUND",
                    "Resource requested at /beers/" + beerId + "/versions was not found",
                    List.of(new ErrorDetail("id", "Requested resource with the specified identifier was not found"))
            );
        }

        return beerVersionRepository.findByBeerId(beerId).stream()
                .map(beerVersionMapper::toResponse)
                .toList();
    }

    public BeerVersionPagedResponse listVersionsByStatus(String status, int page, int size) {
        if (!DomainValidationPredicates.IS_VALID_PAGE_INDEX.test(page)
                || !DomainValidationPredicates.IS_VALID_PAGE_SIZE.test(size)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Invalid pagination parameters",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
            );
        }

        List<BeerVersionResponse> filtered = beerVersionRepository.findByStatus(status).stream()
                .map(beerVersionMapper::toResponse)
                .toList();

        return beerVersionMapper.toPagedResponse(filtered, page, size);
    }

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
            BeerVersion version = beerVersionRepository.findById(versionId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "NOT_FOUND",
                            "Resource requested at /beer-versions/" + versionId + "/votes was not found",
                            List.of(new ErrorDetail("id", "Requested resource with the specified identifier was not found"))
                    ));

            if (voterId.equals(version.authorId())) {
                log.warn("Author attempted to vote on own version",
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

            VersionVote vote = new VersionVote(
                    UUID.randomUUID(),
                    versionId,
                    voterId,
                    request.voteType(),
                    Instant.now()
            );

            if (versionVoteRepository.existsByUserIdAndVersionId(voterId, versionId)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_VOTE_CONFLICT",
                        "User has already cast a vote for this version",
                        List.of(new ErrorDetail("id", "A vote from the current user has already been recorded for this version"))
                );
            }
            versionVoteRepository.saveAndFlush(vote);

            int newConfirmVotes = version.confirmVotes() + ("CONFIRM".equals(request.voteType()) ? 1 : 0);
            int newRejectVotes = version.rejectVotes() + ("REJECT".equals(request.voteType()) ? 1 : 0);
            String newStatus = version.status();
            String message = "Vote recorded successfully";

            if ("UNVERIFIED".equals(version.status()) && newConfirmVotes >= CONSENSUS_THRESHOLD) {
                newStatus = "VERIFIED";
                message = "Version successfully verified by community consensus (threshold N=5 reached)";
                applyVerifiedVersionToCatalog(version);

                log.info("Beer version verified by community consensus",
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

                log.info("Beer version rejected by community consensus",
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
        }
    }

    private void applyVerifiedVersionToCatalog(BeerVersion version) {
        beerRepository.findById(version.beerId()).ifPresent(existingBeer -> {
            BeerVersionCreateRequest p = version.payload();
            List<BeerPackaging> updatedPackagings = new ArrayList<>(existingBeer.packagings());
            if (p != null && p.packagings() != null && !p.packagings().isEmpty()) {
                for (BeerPackagingCreateRequest pkgReq : p.packagings()) {
                    updatedPackagings.add(beerMapper.toPackagingEntity(existingBeer.id(), pkgReq));
                }
            }

            Beer updatedBeer = new Beer(
                    existingBeer.id(),
                    p != null && p.name() != null ? p.name().trim() : existingBeer.name(),
                    p != null && p.brewery() != null ? p.brewery().trim() : existingBeer.brewery(),
                    p != null && p.style() != null ? p.style().trim() : existingBeer.style(),
                    p != null && p.abv() != null ? p.abv() : existingBeer.abv(),
                    p != null && p.beerType() != null ? p.beerType() : existingBeer.beerType(),
                    p != null && p.description() != null ? p.description() : existingBeer.description(),
                    p != null && p.country() != null ? p.country() : existingBeer.country(),
                    p != null && p.imageUrl() != null ? p.imageUrl() : existingBeer.imageUrl(),
                    "VERIFIED",
                    existingBeer.ratingAvg(),
                    existingBeer.reviewsCount(),
                    List.copyOf(updatedPackagings),
                    existingBeer.createdAt()
            );
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

        if (request.packagings() != null) {
            String effectiveBeerType = request.beerType() != null ? request.beerType() : beer.beerType();
            for (int i = 0; i < request.packagings().size(); i++) {
                BeerPackagingCreateRequest pkg = request.packagings().get(i);
                String fieldPrefix = "packagings[" + i + "]";

                if (!DomainValidationPredicates.IS_VALID_PACKAGING_VOLUME.test(pkg.volumeMl())) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "VOLUME_OUT_OF_BOUNDS",
                            "Packaging volume must be strictly between 50 and 10000 ml",
                            List.of(new ErrorDetail(fieldPrefix + ".volume_ml", "Volume must be between 50 and 10000 ml"))
                    );
                }

                if (DomainValidationPredicates.IS_DRAFT_PACKAGE_INCOMPATIBLE.test(effectiveBeerType, pkg.packageType())) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "INVALID_PACKAGE_TYPE_FOR_DRAFT",
                            "All packagings for DRAFT beer must have package_type DRAFT",
                            List.of(new ErrorDetail(fieldPrefix + ".package_type", "Incompatible package_type for DRAFT"))
                    );
                }

                if (DomainValidationPredicates.HAS_BARCODE.test(pkg.barcode())) {
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
