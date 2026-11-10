package com.beertracker.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<ErrorDetail> details,
        Instant timestamp,
        String traceId
) {
}
