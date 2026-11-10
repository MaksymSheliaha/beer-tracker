package com.beertracker.model;

import com.beertracker.dto.BeerVersionCreateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class BeerVersionPayloadConverter implements AttributeConverter<BeerVersionCreateRequest, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public String convertToDatabaseColumn(BeerVersionCreateRequest attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize BeerVersionCreateRequest payload to JSON", e);
        }
    }

    @Override
    public BeerVersionCreateRequest convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, BeerVersionCreateRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize BeerVersionCreateRequest payload from JSON", e);
        }
    }
}
