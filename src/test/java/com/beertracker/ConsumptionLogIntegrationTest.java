package com.beertracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConsumptionLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("BDD-SCN-001 / REQ-F-006 / AC-006.1: POST /api/v1/consumption-entries calculates total_volume_ml = 1000 and returns 201 Created")
    void shouldRecordConsumptionEntryAndCalculateTotalVolume_BddScn001() throws Exception {
        String createBeerPayload = """
                {
                  "name": "Mova Dark Stout",
                  "brewery": "Mova Brewing Co.",
                  "style": "Oatmeal Stout",
                  "abv": 5.8,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "CAN",
                      "barcode": "4820202600018"
                    }
                  ]
                }
                """;

        MvcResult beerResult = mockMvc.perform(post("/api/v1/beers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBeerPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode beerJson = objectMapper.readTree(beerResult.getResponse().getContentAsString());
        String beerId = beerJson.get("id").asText();
        String packagingId = beerJson.get("packagings").get(0).get("id").asText();
        String consumedAt = Instant.now().minus(Duration.ofHours(2)).toString();

        String consumptionPayload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 2,
                  "consumed_at": "%s"
                }
                """.formatted(beerId, packagingId, consumedAt);

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consumptionPayload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.beer_id").value(beerId))
                .andExpect(jsonPath("$.packaging_id").value(packagingId))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.total_volume_ml").value(1000))
                .andExpect(jsonPath("$.consumed_at").value(consumedAt))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.collection_added").value(true))
                .andExpect(jsonPath("$.wishlist_autoremoved").isBoolean());

        mockMvc.perform(get("/api/v1/consumption-entries")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].total_volume_ml").value(1000))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    @DisplayName("REQ-F-006 / AC-006.2: POST /api/v1/consumption-entries with future consumed_at returns 422 Unprocessable Entity")
    void shouldReturn422WhenConsumedAtIsInFuture() throws Exception {
        String futureTime = Instant.now().plus(Duration.ofDays(1)).toString();
        String payload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 1,
                  "consumed_at": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), futureTime);

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSUMPTION_TIME_IN_FUTURE"))
                .andExpect(jsonPath("$.details[0].field").value("consumed_at"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-006 / AC-006.4: POST /api/v1/consumption-entries with quantity < 1 or > 100 returns 422 Unprocessable Entity")
    void shouldReturn422WhenQuantityIsOutOfBounds() throws Exception {
        String consumedAt = Instant.now().minus(Duration.ofHours(1)).toString();
        String payload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 0,
                  "consumed_at": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), consumedAt);

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("BDD-SCN-005 / REQ-F-007 / AC-007.1: PUT, PATCH, DELETE on /api/v1/consumption-entries/{id} return 405 Method Not Allowed")
    void shouldRejectPutPatchAndDeleteOnConsumptionEntriesWith405_BddScn005() throws Exception {
        String entryId = "c0a80123-0001-4444-9999-0123456789ab";

        mockMvc.perform(put("/api/v1/consumption-entries/" + entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(patch("/api/v1/consumption-entries/" + entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(delete("/api/v1/consumption-entries/" + entryId))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
