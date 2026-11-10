package com.beertracker.service;

import com.beertracker.dto.BeerCreateRequest;
import com.beertracker.dto.BeerPackagingCreateRequest;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.BeerMapper;
import com.beertracker.model.Beer;
import com.beertracker.repository.BeerRepository;
import com.beertracker.validation.DomainValidationPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class BeerService {

    private static final Logger log = LoggerFactory.getLogger(BeerService.class);

    private final BeerRepository beerRepository;
    private final BeerMapper beerMapper;

    public BeerService(BeerRepository beerRepository, BeerMapper beerMapper) {
        this.beerRepository = beerRepository;
        this.beerMapper = beerMapper;
    }

    public BeerResponse createBeer(BeerCreateRequest request) {
        validateCreateRequest(request);

        Beer beer = beerMapper.toEntity(request);
        beerRepository.save(beer);

        log.info("Beer registered in catalog",
                kv("event", "beer_created"),
                kv("beer_id", beer.id().toString()),
                kv("name", beer.name()),
                kv("brewery", beer.brewery()),
                kv("beer_type", beer.beerType()),
                kv("status", "201"));

        return beerMapper.toResponse(beer);
    }

    public BeerPagedResponse listBeers(String query, int page, int size) {
        long startNs = System.nanoTime();
        validatePagination(page, size);

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<BeerResponse> filtered = beerRepository.findAllVisibleInCatalog().stream()
                .filter(b -> normalizedQuery.isEmpty()
                        || b.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || b.brewery().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(beerMapper::toResponse)
                .toList();

        BeerPagedResponse pagedResponse = beerMapper.toPagedResponse(filtered, page, size);

        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;
        log.info("Catalog search executed",
                kv("event", "catalog_search"),
                kv("query", query),
                kv("hits_count", pagedResponse.items().size()),
                kv("duration_ms", durationMs),
                kv("status", "200"));

        return pagedResponse;
    }

    private void validatePagination(int page, int size) {
        if (!DomainValidationPredicates.IS_VALID_PAGE_INDEX.test(page)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Parameter 'page' must not be negative",
                    List.of(new ErrorDetail("page", "Must be greater than or equal to 0"))
            );
        }
        if (!DomainValidationPredicates.IS_VALID_PAGE_SIZE.test(size)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_SIZE",
                    "Parameter 'size' exceeds maximum allowed limit of 100",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
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
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_BEER_NAME_CONFLICT",
                    "Beer with name '%s' and brewery '%s' already exists".formatted(request.name().trim(), request.brewery().trim()),
                    List.of(new ErrorDetail("name", "A product with the same name and brewery already exists in the catalog"))
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
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_PACKAGE_TYPE_FOR_DRAFT",
                    "All packagings for DRAFT beer must have package_type DRAFT",
                    List.of(new ErrorDetail(fieldPrefix + ".package_type", "Found '%s' for beer_type 'DRAFT'".formatted(pkg.packageType())))
            );
        }

        if (DomainValidationPredicates.IS_PACKAGED_DRAFT_INCOMPATIBLE.test(request.beerType(), pkg.packageType())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "PACKAGED beer cannot have package_type DRAFT",
                    List.of(new ErrorDetail(fieldPrefix + ".package_type", "package_type DRAFT is not allowed for PACKAGED beer"))
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
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_BARCODE_CONFLICT",
                        "Штрих-код " + barcode + " уже прив'язаний до іншого товару",
                        List.of(new ErrorDetail(fieldPrefix + ".barcode", "Barcode " + barcode + " is already assigned to " + assignedTo))
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
