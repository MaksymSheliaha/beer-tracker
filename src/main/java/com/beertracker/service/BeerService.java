package com.beertracker.service;

import com.beertracker.dto.BeerCreateRequest;
import com.beertracker.dto.BeerPackagingCreateRequest;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.dto.ErrorDetail;
import com.beertracker.exception.ApiException;
import com.beertracker.mapper.BeerMapper;
import com.beertracker.model.Beer;
import com.beertracker.model.BeerPackaging;
import com.beertracker.repository.BeerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

        UUID beerId = UUID.randomUUID();
        List<BeerPackaging> packagings = new ArrayList<>();
        for (BeerPackagingCreateRequest pkgReq : request.packagings()) {
            packagings.add(new BeerPackaging(
                    UUID.randomUUID(),
                    beerId,
                    pkgReq.volumeMl(),
                    pkgReq.packageType(),
                    pkgReq.barcode() != null ? pkgReq.barcode().trim() : null
            ));
        }

        Beer beer = new Beer(
                beerId,
                request.name().trim(),
                request.brewery().trim(),
                request.style().trim(),
                request.abv(),
                request.beerType(),
                request.description(),
                request.country(),
                request.imageUrl(),
                "UNVERIFIED",
                null,
                0,
                List.copyOf(packagings),
                Instant.now()
        );

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
        if (page < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Parameter 'page' must not be negative",
                    List.of(new ErrorDetail("page", "Must be greater than or equal to 0"))
            );
        }
        if (size < 1 || size > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_SIZE",
                    "Parameter 'size' exceeds maximum allowed limit of 100",
                    List.of(new ErrorDetail("size", "Must be between 1 and 100"))
            );
        }

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<BeerResponse> filtered = beerRepository.findAll().stream()
                .filter(b -> normalizedQuery.isEmpty()
                        || b.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || b.brewery().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(beerMapper::toResponse)
                .toList();

        int totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<BeerResponse> pageItems = filtered.subList(fromIndex, toIndex);

        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;
        log.info("Catalog search executed",
                kv("event", "catalog_search"),
                kv("query", query),
                kv("hits_count", pageItems.size()),
                kv("duration_ms", durationMs),
                kv("status", "200"));

        return new BeerPagedResponse(
                pageItems,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
    }

    private void validateCreateRequest(BeerCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Required field 'name' is missing or empty",
                    List.of(new ErrorDetail("name", "Field is required and cannot be blank"))
            );
        }
        if (request.brewery() == null || request.brewery().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Required field 'brewery' is missing or empty",
                    List.of(new ErrorDetail("brewery", "Field is required and cannot be blank"))
            );
        }
        if (request.style() == null || request.style().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Required field 'style' is missing or empty",
                    List.of(new ErrorDetail("style", "Field is required and cannot be blank"))
            );
        }
        if (request.abv() == null || request.abv() < 0.0 || request.abv() > 100.0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNPROCESSABLE_ENTITY",
                    "ABV must be between 0.0 and 100.0",
                    List.of(new ErrorDetail("abv", "ABV must be between 0.0 and 100.0"))
            );
        }
        if (request.beerType() == null || (!"PACKAGED".equals(request.beerType()) && !"DRAFT".equals(request.beerType()))) {
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
            BeerPackagingCreateRequest pkg = request.packagings().get(i);
            String fieldPrefix = "packagings[" + i + "]";

            if (pkg.volumeMl() == null || pkg.volumeMl() < 50 || pkg.volumeMl() > 10000) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "VOLUME_OUT_OF_BOUNDS",
                        "Packaging volume must be strictly between 50 and 10000 ml",
                        List.of(new ErrorDetail(fieldPrefix + ".volume_ml", "Volume must be between 50 and 10000 ml"))
                );
            }

            if ("DRAFT".equals(request.beerType()) && !"DRAFT".equals(pkg.packageType())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "INVALID_PACKAGE_TYPE_FOR_DRAFT",
                        "All packagings for DRAFT beer must have package_type DRAFT",
                        List.of(new ErrorDetail(fieldPrefix + ".package_type", "Found '%s' for beer_type 'DRAFT'".formatted(pkg.packageType())))
                );
            }

            if ("PACKAGED".equals(request.beerType()) && "DRAFT".equals(pkg.packageType())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "PACKAGED beer cannot have package_type DRAFT",
                        List.of(new ErrorDetail(fieldPrefix + ".package_type", "package_type DRAFT is not allowed for PACKAGED beer"))
                );
            }

            if (pkg.barcode() != null && !pkg.barcode().isBlank()) {
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
    }
}
