package com.beertracker.validation;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Isolated domain validation predicates for Beer Catalog (REQ-F-001, REQ-F-002)
 * and Consumption Log (REQ-F-006, REQ-NF-008).
 */
public final class DomainValidationPredicates {

    public static final int MIN_PACKAGING_VOLUME_ML = 50;
    public static final int MAX_PACKAGING_VOLUME_ML = 10000;
    public static final int MIN_CONSUMPTION_QUANTITY = 1;
    public static final int MAX_CONSUMPTION_QUANTITY = 100;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{8,14}$");
    private static final Set<String> ALLOWED_BEER_TYPES = Set.of("PACKAGED", "DRAFT");

    private DomainValidationPredicates() {
    }

    public static final Predicate<String> IS_NON_BLANK_TEXT =
            value -> value != null && !value.isBlank();

    public static final Predicate<Integer> IS_VALID_PACKAGING_VOLUME =
            volumeMl -> volumeMl != null
                    && volumeMl >= MIN_PACKAGING_VOLUME_ML
                    && volumeMl <= MAX_PACKAGING_VOLUME_ML;

    public static final Predicate<String> HAS_BARCODE =
            barcode -> barcode != null && !barcode.isBlank();

    public static final Predicate<String> IS_VALID_BARCODE_FORMAT =
            barcode -> HAS_BARCODE.test(barcode) && BARCODE_PATTERN.matcher(barcode.trim()).matches();

    public static final Predicate<Double> IS_VALID_ABV =
            abv -> abv != null && abv >= 0.0 && abv <= 100.0;

    public static final Predicate<String> IS_VALID_BEER_TYPE =
            beerType -> beerType != null && ALLOWED_BEER_TYPES.contains(beerType);

    public static final BiPredicate<String, String> IS_DRAFT_PACKAGE_INCOMPATIBLE =
            (beerType, packageType) -> "DRAFT".equals(beerType) && !"DRAFT".equals(packageType);

    public static final BiPredicate<String, String> IS_PACKAGED_DRAFT_INCOMPATIBLE =
            (beerType, packageType) -> "PACKAGED".equals(beerType) && "DRAFT".equals(packageType);

    public static final Predicate<Integer> IS_VALID_PAGE_INDEX =
            page -> page != null && page >= 0;

    public static final Predicate<Integer> IS_VALID_PAGE_SIZE =
            size -> size != null && size >= MIN_PAGE_SIZE && size <= MAX_PAGE_SIZE;

    public static final Predicate<Integer> IS_VALID_CONSUMPTION_QUANTITY =
            quantity -> quantity != null
                    && quantity >= MIN_CONSUMPTION_QUANTITY
                    && quantity <= MAX_CONSUMPTION_QUANTITY;

    public static final BiPredicate<Instant, Instant> IS_CONSUMED_AT_IN_FUTURE =
            (consumedAt, now) -> consumedAt != null && now != null && consumedAt.isAfter(now);

    public static final BiPredicate<Instant, Instant> IS_CONSUMED_AT_OLDER_THAN_ONE_YEAR =
            (consumedAt, now) -> consumedAt != null
                    && now != null
                    && consumedAt.isBefore(now.minus(Duration.ofDays(365)));
}
