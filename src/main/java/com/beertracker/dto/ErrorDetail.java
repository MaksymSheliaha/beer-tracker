package com.beertracker.dto;

public record ErrorDetail(
        String field,
        String issue
) {
}
