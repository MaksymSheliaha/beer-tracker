package com.beertracker.validation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Isolated domain validation predicates for Beer Catalog ({@code REQ-F-001}, {@code REQ-F-002},
 * {@code REQ-F-015}), Consumption Log ({@code REQ-F-006}, {@code REQ-NF-008}), Reviews
 * ({@code REQ-F-008}), and Authentication ({@code REQ-NF-004}, {@code REQ-NF-006}).
 */
public final class DomainValidationPredicates {

    /**
     * Minimum packaging volume in milliliters ({@code AC-001.2}).
     */
    public static final int MIN_PACKAGING_VOLUME_ML = 50;

    /**
     * Maximum packaging volume in milliliters ({@code AC-001.2}).
     */
    public static final int MAX_PACKAGING_VOLUME_ML = 10000;

    /**
     * Minimum consumption quantity per log entry ({@code AC-006.4}).
     */
    public static final int MIN_CONSUMPTION_QUANTITY = 1;

    /**
     * Maximum consumption quantity per log entry ({@code AC-006.4}).
     */
    public static final int MAX_CONSUMPTION_QUANTITY = 100;

    /**
     * Minimum allowed pagination page size.
     */
    public static final int MIN_PAGE_SIZE = 1;

    /**
     * Maximum allowed pagination page size.
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Minimum password character length.
     */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * Maximum password character length.
     */
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{8,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> ALLOWED_BEER_TYPES = Set.of("PACKAGED", "DRAFT");

    /**
     * Verifies a string is non-null and contains non-whitespace characters.
     */
    public static final Predicate<String> IS_NON_BLANK_TEXT =
            value -> value != null && !value.isBlank();

    /**
     * Validates packaging volume lies within {@code [50, 10000]} ml ({@code AC-001.2}).
     */
    public static final Predicate<Integer> IS_VALID_PACKAGING_VOLUME =
            volumeMl -> volumeMl != null
                    && volumeMl >= MIN_PACKAGING_VOLUME_ML
                    && volumeMl <= MAX_PACKAGING_VOLUME_ML;

    /**
     * Checks whether a non-blank barcode value was supplied.
     */
    public static final Predicate<String> HAS_BARCODE =
            barcode -> barcode != null && !barcode.isBlank();

    /**
     * Validates barcode conforms to 8–14 numeric digits (EAN-8, UPC-A, EAN-13, GTIN-14).
     */
    public static final Predicate<String> IS_VALID_BARCODE_FORMAT =
            barcode -> HAS_BARCODE.test(barcode) && BARCODE_PATTERN.matcher(barcode.trim()).matches();

    /**
     * Validates alcohol by volume (ABV) percentage is within {@code [0.0, 100.0]}.
     */
    public static final Predicate<Double> IS_VALID_ABV =
            abv -> abv != null && abv >= 0.0 && abv <= 100.0;

    /**
     * Validates optional {@code (minAbv, maxAbv)} bounds and ensures {@code minAbv <= maxAbv}
     * ({@code AC-015.2}).
     */
    public static final BiPredicate<Double, Double> IS_VALID_ABV_RANGE =
            (minAbv, maxAbv) -> (minAbv == null || IS_VALID_ABV.test(minAbv))
                    && (maxAbv == null || IS_VALID_ABV.test(maxAbv))
                    && (minAbv == null || maxAbv == null || minAbv <= maxAbv);

    /**
     * Validates {@code beer_type} is either {@code PACKAGED} or {@code DRAFT}.
     */
    public static final Predicate<String> IS_VALID_BEER_TYPE =
            beerType -> beerType != null && ALLOWED_BEER_TYPES.contains(beerType);

    /**
     * Detects incompatible non-{@code DRAFT} packaging assigned to a {@code DRAFT} beer ({@code AC-001.3}).
     */
    public static final BiPredicate<String, String> IS_DRAFT_PACKAGE_INCOMPATIBLE =
            (beerType, packageType) -> "DRAFT".equals(beerType) && !"DRAFT".equals(packageType);

    /**
     * Detects incompatible {@code DRAFT} packaging assigned to a {@code PACKAGED} beer ({@code AC-001.4}).
     */
    public static final BiPredicate<String, String> IS_PACKAGED_DRAFT_INCOMPATIBLE =
            (beerType, packageType) -> "PACKAGED".equals(beerType) && "DRAFT".equals(packageType);

    /**
     * Validates zero-based page index is non-negative.
     */
    public static final Predicate<Integer> IS_VALID_PAGE_INDEX =
            page -> page != null && page >= 0;

    /**
     * Validates page size lies within {@code [1, 100]}.
     */
    public static final Predicate<Integer> IS_VALID_PAGE_SIZE =
            size -> size != null && size >= MIN_PAGE_SIZE && size <= MAX_PAGE_SIZE;

    /**
     * Validates consumption quantity lies within {@code [1, 100]} ({@code AC-006.4}).
     */
    public static final Predicate<Integer> IS_VALID_CONSUMPTION_QUANTITY =
            quantity -> quantity != null
                    && quantity >= MIN_CONSUMPTION_QUANTITY
                    && quantity <= MAX_CONSUMPTION_QUANTITY;

    /**
     * Detects consumption timestamps strictly after current server time ({@code AC-006.2}).
     */
    public static final BiPredicate<Instant, Instant> IS_CONSUMED_AT_IN_FUTURE =
            (consumedAt, now) -> consumedAt != null && now != null && consumedAt.isAfter(now);

    /**
     * Detects consumption timestamps older than 365 days relative to {@code now} ({@code AC-006.3}).
     */
    public static final BiPredicate<Instant, Instant> IS_CONSUMED_AT_OLDER_THAN_ONE_YEAR =
            (consumedAt, now) -> consumedAt != null
                    && now != null
                    && consumedAt.isBefore(now.minus(Duration.ofDays(365)));

    /**
     * Validates syntactic email address format.
     */
    public static final Predicate<String> IS_VALID_EMAIL =
            email -> IS_NON_BLANK_TEXT.test(email) && EMAIL_PATTERN.matcher(email.trim()).matches();

    /**
     * Validates raw password length lies within {@code [8, 128]} characters.
     */
    public static final Predicate<String> IS_VALID_PASSWORD =
            password -> password != null
                    && password.length() >= MIN_PASSWORD_LENGTH
                    && password.length() <= MAX_PASSWORD_LENGTH;

    /**
     * Validates a string corresponds to a known IANA timezone identifier in {@link ZoneId}.
     */
    public static final Predicate<String> IS_VALID_IANA_TIMEZONE =
            timezone -> IS_NON_BLANK_TEXT.test(timezone) && ZoneId.getAvailableZoneIds().contains(timezone.trim());

    /**
     * Validates review rating lies within {@code [1.0, 5.0]} in {@code 0.5} increments ({@code AC-008.2}).
     */
    public static final Predicate<Double> IS_VALID_REVIEW_RATING =
            rating -> rating != null
                    && rating >= 1.0
                    && rating <= 5.0
                    && Math.abs(rating * 2.0 - Math.round(rating * 2.0)) < 1e-6;

    private DomainValidationPredicates() {
    }

    /**
     * Builds an inclusive ABV range predicate supporting open-ended lower or upper bounds
     * ({@code AC-015.1}).
     *
     * @param minAbv optional inclusive lower bound
     * @param maxAbv optional inclusive upper bound
     * @return predicate evaluating whether a beer's ABV satisfies both supplied bounds
     */
    public static Predicate<Double> matchesAbvRange(Double minAbv, Double maxAbv) {
        return abv -> abv != null
                && (minAbv == null || abv >= minAbv)
                && (maxAbv == null || abv <= maxAbv);
    }
}
