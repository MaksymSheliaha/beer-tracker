package com.beertracker.service;

import com.beertracker.dto.BeerCreateRequest;
import com.beertracker.dto.BeerPackagingCreateRequest;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.BeerMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerVersion;
import com.beertracker.repository.BeerRepository;
import com.beertracker.repository.BeerVersionRepository;
import com.beertracker.validation.DomainValidationPredicates;
import com.beertracker.validation.PaginationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Service managing canonical {@link Beer} catalog registration, preventive deduplication,
 * ABV-range filtering, and draft-aware product retrieval
 * ({@code REQ-F-001}, {@code REQ-F-002}, {@code REQ-F-003}, {@code REQ-F-015}).
 */
@Service
public class BeerService {

    private static final Logger LOG = LoggerFactory.getLogger(BeerService.class);

    private final BeerRepository beerRepository;
    private final BeerVersionRepository beerVersionRepository;
    private final BeerMapper beerMapper;

    /**
     * Constructs the catalog service.
     *
     * @param beerRepository        repository storing catalog beers
     * @param beerVersionRepository repository storing proposed version drafts
     * @param beerMapper            mapper between beer entities and DTOs
     */
    public BeerService(
            BeerRepository beerRepository,
            BeerVersionRepository beerVersionRepository,
            BeerMapper beerMapper
    ) {
        this.beerRepository = beerRepository;
        this.beerVersionRepository = beerVersionRepository;
        this.beerMapper = beerMapper;
    }

    /**
     * Retrieves an active catalog beer by UUID, returning the latest unverified draft overlay
     * unless {@code verifiedOnly} is {@code true} ({@code AC-003.3}).
     *
     * @param id           beer UUID
     * @param verifiedOnly when {@code true}, returns only the base catalog state without pending draft overlay
     * @return {@link BeerResponse} for the requested beer
     * @throws ApiException with {@code 404 NOT_FOUND} ({@code BEER_NOT_FOUND}) if missing,
     *                      soft-deleted, or merged
     */
    @Transactional(readOnly = true)
    public BeerResponse getBeerById(UUID id, boolean verifiedOnly) {
        Beer beer = beerRepository.findActiveById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BEER_NOT_FOUND",
                        "Beer with specified identifier was not found",
                        List.of(new ErrorDetail("id", "No product found with identifier '" + id + "'"))
                ));

        if (!verifiedOnly) {
            Optional<BeerVersion> draftOpt = beerVersionRepository.findActiveUnverifiedByBeerId(id);
            if (draftOpt.isPresent() && draftOpt.get().payload() != null) {
                Beer effectiveBeer = beerMapper.mergeWithVersionPayload(
                        beer,
                        draftOpt.get().payload(),
                        beer.status()
                );
                return beerMapper.toResponse(effectiveBeer);
            }
        }

        return beerMapper.toResponse(beer);
    }

    /**
     * Validates domain invariants and preventive uniqueness rules, then registers a new
     * {@code UNVERIFIED} beer product with its packagings ({@code AC-001.1}..{@code AC-002.2}).
     *
     * @param request beer creation payload
     * @return persisted {@link BeerResponse}
     * @throws ApiException with {@code 400 BAD_REQUEST} for missing text fields,
     *                      {@code 409 CONFLICT} for duplicate {@code (brewery, name)} or barcode,
     *                      or {@code 422 UNPROCESSABLE_ENTITY} for packaging/ABV/type rule violations
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BeerResponse createBeer(BeerCreateRequest request) {
        validateCreateRequest(request);

        Beer beer = beerMapper.toEntity(request);
        beerRepository.save(beer);

        LOG.info("Beer registered in catalog",
                kv("event", "beer_created"),
                kv("beer_id", beer.id().toString()),
                kv("name", beer.name()),
                kv("brewery", beer.brewery()),
                kv("beer_type", beer.beerType()),
                kv("status", "201"));

        return beerMapper.toResponse(beer);
    }

    /**
     * Searches visible catalog beers without ABV bounds filtering.
     *
     * @param query optional substring matched against name or brewery
     * @param page  zero-based page index
     * @param size  page size (1..100)
     * @return paginated {@link BeerPagedResponse}
     */
    @Transactional(readOnly = true)
    public BeerPagedResponse listBeers(String query, int page, int size) {
        return listBeers(query, null, null, page, size);
    }

    /**
     * Searches and filters visible catalog beers by text query and inclusive ABV range
     * ({@code REQ-F-001}, {@code REQ-F-015}).
     *
     * @param query  optional case-insensitive search substring against name or brewery
     * @param minAbv optional inclusive minimum ABV percentage in {@code [0.0, 100.0]}
     * @param maxAbv optional inclusive maximum ABV percentage in {@code [0.0, 100.0]}
     * @param page   zero-based page index
     * @param size   page size (1..100)
     * @return paginated {@link BeerPagedResponse}
     * @throws ApiException with {@code 400 BAD_REQUEST} for invalid pagination or {@code INVALID_ABV_RANGE}
     */
    @Transactional(readOnly = true)
    public BeerPagedResponse listBeers(String query, Double minAbv, Double maxAbv, int page, int size) {
        long startNs = System.nanoTime();
        PaginationHelper.validate(page, size);
        validateAbvRange(minAbv, maxAbv);

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<BeerResponse> filtered = beerRepository.findAllVisibleByAbvRange(minAbv, maxAbv).stream()
                .filter(b -> normalizedQuery.isEmpty()
                        || b.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || b.brewery().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(beerMapper::toResponse)
                .toList();

        BeerPagedResponse pagedResponse = beerMapper.toPagedResponse(filtered, page, size);

        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;
        LOG.info("Catalog search executed",
                kv("event", "catalog_search"),
                kv("query", query),
                kv("min_abv", minAbv),
                kv("max_abv", maxAbv),
                kv("hits_count", pagedResponse.items().size()),
                kv("duration_ms", durationMs),
                kv("status", "200"));

        return pagedResponse;
    }

    private void validateAbvRange(Double minAbv, Double maxAbv) {
        if (!DomainValidationPredicates.IS_VALID_ABV_RANGE.test(minAbv, maxAbv)) {
            if (minAbv != null && !DomainValidationPredicates.IS_VALID_ABV.test(minAbv)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_ABV_RANGE",
                        "Parameter 'min_abv' must be between 0.0 and 100.0",
                        List.of(new ErrorDetail("min_abv", "Must be between 0.0 and 100.0"))
                );
            }
            if (maxAbv != null && !DomainValidationPredicates.IS_VALID_ABV.test(maxAbv)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_ABV_RANGE",
                        "Parameter 'max_abv' must be between 0.0 and 100.0",
                        List.of(new ErrorDetail("max_abv", "Must be between 0.0 and 100.0"))
                );
            }
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ABV_RANGE",
                    "Parameter 'min_abv' cannot be greater than 'max_abv'",
                    List.of(new ErrorDetail("min_abv", "min_abv (" + minAbv + ") exceeds max_abv (" + maxAbv + ")"))
            );
        }
    }

    private void validateCreateRequest(BeerCreateRequest request) {
        requireNonBlank(request != null ? request.name() : null, "name");
        requireNonBlank(request.brewery(), "brewery");
        requireNonBlank(request.style(), "style");

        if (!DomainValidationPredicates.IS_VALID_ABV.test(request.abv())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "ABV must be between 0.0 and 100.0",
                    List.of(new ErrorDetail("abv", "ABV must be between 0.0 and 100.0"))
            );
        }
        if (!DomainValidationPredicates.IS_VALID_BEER_TYPE.test(request.beerType())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "Invalid beer_type",
                    List.of(new ErrorDetail("beer_type", "Must be PACKAGED or DRAFT"))
            );
        }
        if (request.packagings() == null || request.packagings().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "At least one packaging option is required",
                    List.of(new ErrorDetail("packagings", "Must contain at least 1 packaging"))
            );
        }

        Optional<Beer> duplicateByName = beerRepository.findByBreweryAndName(request.brewery(), request.name());
        if (duplicateByName.isPresent()) {
            String message = "Beer with name '%s' and brewery '%s' already exists"
                    .formatted(request.name().trim(), request.brewery().trim());
            String issue = "A product with the same name and brewery already exists in the catalog";
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_BEER_NAME_CONFLICT",
                    message,
                    List.of(new ErrorDetail("name", issue))
            );
        }

        Set<String> requestBarcodes = new HashSet<>();
        for (int i = 0; i < request.packagings().size(); i++) {
            validatePackagingOption(request, request.packagings().get(i), i, requestBarcodes);
        }
    }

    private void validatePackagingOption(
            BeerCreateRequest request,
            BeerPackagingCreateRequest pkg,
            int index,
            Set<String> requestBarcodes
    ) {
        String fieldPrefix = "packagings[" + index + "]";

        if (!DomainValidationPredicates.IS_VALID_PACKAGING_VOLUME.test(pkg.volumeMl())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VOLUME_OUT_OF_BOUNDS",
                    "Packaging volume must be strictly between 50 and 10000 ml",
                    List.of(new ErrorDetail(fieldPrefix + ".volume_ml", "Volume must be between 50 and 10000 ml"))
            );
        }

        if (DomainValidationPredicates.IS_DRAFT_PACKAGE_INCOMPATIBLE.test(request.beerType(), pkg.packageType())) {
            String issue = "Found '%s' for beer_type 'DRAFT'".formatted(pkg.packageType());
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_PACKAGE_TYPE_FOR_DRAFT",
                    "All packagings for DRAFT beer must have package_type DRAFT",
                    List.of(new ErrorDetail(fieldPrefix + ".package_type", issue))
            );
        }

        if (DomainValidationPredicates.IS_PACKAGED_DRAFT_INCOMPATIBLE.test(request.beerType(), pkg.packageType())) {
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
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "INVALID_BARCODE_FORMAT",
                        "Barcode must consist of 8 to 14 digits",
                        List.of(new ErrorDetail(fieldPrefix + ".barcode", "Barcode must match ^\\d{8,14}$"))
                );
            }
            String barcode = pkg.barcode().trim();
            Optional<Beer> existingByBarcode = beerRepository.findByBarcode(barcode);
            if (existingByBarcode.isPresent() || !requestBarcodes.add(barcode)) {
                String assignedTo = existingByBarcode.map(Beer::name).orElse(request.name());
                String issue = "Barcode " + barcode + " is already assigned to " + assignedTo;
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_BARCODE_CONFLICT",
                        "Штрих-код " + barcode + " уже прив'язаний до іншого товару",
                        List.of(new ErrorDetail(fieldPrefix + ".barcode", issue))
                );
            }
        }
    }

    private void requireNonBlank(String value, String fieldName) {
        if (!DomainValidationPredicates.IS_NON_BLANK_TEXT.test(value)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Required field '%s' is missing or empty".formatted(fieldName),
                    List.of(new ErrorDetail(fieldName, "Field is required and cannot be blank"))
            );
        }
    }
}
