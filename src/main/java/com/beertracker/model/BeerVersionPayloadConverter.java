package com.beertracker.model;

import com.beertracker.dto.BeerVersionCreateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} serializing typed {@link BeerVersionCreateRequest}
 * draft payloads to and from JSON text columns in the {@code beer_versions} table.
 */
@Converter
public class BeerVersionPayloadConverter implements AttributeConverter<BeerVersionCreateRequest, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    /**
     * Serializes a {@link BeerVersionCreateRequest} payload to a JSON string for storage.
     *
     * @param attribute typed draft payload (may be {@code null})
     * @return JSON string representation, or {@code null}
     */
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

    /**
     * Deserializes a persisted JSON string back into a typed {@link BeerVersionCreateRequest}.
     *
     * @param dbData JSON string loaded from database column (may be {@code null} or blank)
     * @return deserialized {@link BeerVersionCreateRequest}, or {@code null}
     */
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
