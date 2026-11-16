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

    private String userAuthHeader(UUID userId, String email) {
        return "Bearer " + JwtTestTokenProvider.createUserToken(userId, email);
    }

    private String[] createSampleBeer(String authHeader) throws Exception {
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
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBeerPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode beerJson = objectMapper.readTree(beerResult.getResponse().getContentAsString());
        return new String[]{
                beerJson.get("id").asText(),
                beerJson.get("packagings").get(0).get("id").asText()
        };
    }

    @Test
    @DisplayName("BDD-SCN-001 / REQ-F-006 / AC-006.1: Record consumption calculates total_volume_ml")
    void shouldRecordConsumptionEntryAndCalculateTotalVolumeBddScn001() throws Exception {
        String authHeader = userAuthHeader(UUID.randomUUID(), "consumer@example.com");
        String[] ids = createSampleBeer(authHeader);
        String beerId = ids[0];
        String packagingId = ids[1];
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
                        .header("Authorization", authHeader)
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
                        .header("Authorization", authHeader)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].total_volume_ml").value(1000))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    @DisplayName("REQ-NF-004: Unauthenticated consumption GET/POST requests return 401 UNAUTHORIZED")
    void shouldReturn401WhenAccessingConsumptionEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/consumption-entries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/consumption-entries/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("REQ-NF-005 / AC-NF-005.1: Accessing another user's entry returns 404 Not Found")
    void shouldIsolateConsumptionEntriesBetweenUsersWith404() throws Exception {
        String ownerHeader = userAuthHeader(UUID.randomUUID(), "owner@example.com");
        String otherUserHeader = userAuthHeader(UUID.randomUUID(), "intruder@example.com");
        String[] ids = createSampleBeer(ownerHeader);

        String consumptionPayload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 1,
                  "consumed_at": "%s"
                }
                """.formatted(ids[0], ids[1], Instant.now().minus(Duration.ofHours(1)).toString());

        MvcResult created = mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", ownerHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consumptionPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String entryId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/consumption-entries")
                        .header("Authorization", otherUserHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0));

        mockMvc.perform(get("/api/v1/consumption-entries/" + entryId)
                        .header("Authorization", otherUserHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONSUMPTION_ENTRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("REQ-F-006 / AC-006.2: Future consumed_at returns 422 CONSUMPTION_TIME_IN_FUTURE")
    void shouldReturn422WhenConsumedAtIsInFuture() throws Exception {
        String authHeader = userAuthHeader(UUID.randomUUID(), "consumer@example.com");
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
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSUMPTION_TIME_IN_FUTURE"))
                .andExpect(jsonPath("$.details[0].field").value("consumed_at"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-006 / AC-006.3: consumed_at prior to User.created_at returns 422")
    void shouldReturn422WhenConsumedAtPrecedesUserRegistration() throws Exception {
        String registerPayload = """
                {
                  "email": "new.member@example.com",
                  "password": "StrongPassword123!",
                  "timezone": "Europe/Kyiv"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.member@example.com",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("access_token").asText();
        String authHeader = "Bearer " + token;

        String[] ids = createSampleBeer(authHeader);
        String beforeRegistration = Instant.now().minus(Duration.ofHours(2)).toString();
        String payload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 1,
                  "consumed_at": "%s"
                }
                """.formatted(ids[0], ids[1], beforeRegistration);

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSUMPTION_TIME_BEFORE_REGISTRATION"))
                .andExpect(jsonPath("$.details[0].field").value("consumed_at"));
    }

    @Test
    @DisplayName("REQ-F-006 / AC-006.4: Quantity < 1 or > 100 returns 422 INVALID_QUANTITY")
    void shouldReturn422WhenQuantityIsOutOfBounds() throws Exception {
        String authHeader = userAuthHeader(UUID.randomUUID(), "consumer@example.com");
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
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("BDD-SCN-005 / REQ-F-007 / AC-007.1: PUT/PATCH/DELETE return 405 Method Not Allowed")
    void shouldRejectPutPatchAndDeleteOnConsumptionEntriesWith405BddScn005() throws Exception {
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
