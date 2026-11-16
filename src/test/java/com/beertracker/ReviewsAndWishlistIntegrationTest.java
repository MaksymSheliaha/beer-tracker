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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReviewsAndWishlistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode createSampleBeer(String name, String brewery, String barcode) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "brewery": "%s",
                  "style": "Oatmeal Stout",
                  "abv": 5.8,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "CAN",
                      "barcode": "%s"
                    }
                  ]
                }
                """.formatted(name, brewery, barcode);

        String creatorToken = JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "catalog@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("BDD-SCN-003 / REQ-F-008 / AC-008.1: Review without consumption returns 403")
    void shouldRejectReviewWithoutPriorConsumptionBddScn003() throws Exception {
        JsonNode beer = createSampleBeer("Underwood Kyiv Lager", "Underwood Brewery", "4820202600111");
        String beerId = beer.get("id").asText();
        String token = JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "critic@example.com");

        String reviewPayload = """
                {
                  "rating": 4.5,
                  "review_text": "Чудовий класичний лагер"
                }
                """;

        mockMvc.perform(post("/api/v1/beers/" + beerId + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_CONSUMPTION_REQUIRED"))
                .andExpect(jsonPath("$.details[0].field").value("beer_id"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-008 / AC-008.2: Review rating not multiple of 0.5 returns 422")
    void shouldReturn422WhenRatingStepIsInvalid() throws Exception {
        JsonNode beer = createSampleBeer("Mova Dark Stout", "Mova Brewing Co.", "4820202600018");
        String beerId = beer.get("id").asText();
        String packagingId = beer.get("packagings").get(0).get("id").asText();
        String token = JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "taster@example.com");

        String consumptionPayload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 1,
                  "consumed_at": "%s"
                }
                """.formatted(beerId, packagingId, Instant.now().minus(Duration.ofHours(1)).toString());

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consumptionPayload))
                .andExpect(status().isCreated());

        String invalidRatingPayload = """
                {
                  "rating": 4.3,
                  "review_text": "Невірний крок оцінки"
                }
                """;

        mockMvc.perform(post("/api/v1/beers/" + beerId + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRatingPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_RATING_VALUE"))
                .andExpect(jsonPath("$.details[0].field").value("rating"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-008 / AC-008.3: Create (201), duplicate (409), update (200), delete (204)")
    void shouldCreateUpdateAndDeleteReviewAfterConsumption() throws Exception {
        JsonNode beer = createSampleBeer("Mova Dark Stout", "Mova Brewing Co.", "4820202600018");
        String beerId = beer.get("id").asText();
        String packagingId = beer.get("packagings").get(0).get("id").asText();
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokenProvider.createUserToken(userId, "reviewer@example.com");

        String consumptionPayload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 1,
                  "consumed_at": "%s"
                }
                """.formatted(beerId, packagingId, Instant.now().minus(Duration.ofHours(2)).toString());

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consumptionPayload))
                .andExpect(status().isCreated());

        String createReviewPayload = """
                {
                  "rating": 4.5,
                  "review_text": "Чудовий насичений вівсяний стаут, виражений смак шоколаду"
                }
                """;

        mockMvc.perform(post("/api/v1/beers/" + beerId + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.beer_id").value(beerId))
                .andExpect(jsonPath("$.user_id").value(userId.toString()))
                .andExpect(jsonPath("$.rating").value(4.5))
                .andExpect(jsonPath("$.review_text").value("Чудовий насичений вівсяний стаут, виражений смак шоколаду"))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.updated_at").isNotEmpty());

        // Duplicate POST for same beer & user returns 409 Conflict
        mockMvc.perform(post("/api/v1/beers/" + beerId + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        // Update review via PUT -> 200 OK
        String updateReviewPayload = """
                {
                  "rating": 5.0,
                  "review_text": "Після повторної дегустації - оцінка максимальна!"
                }
                """;

        mockMvc.perform(put("/api/v1/beers/" + beerId + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReviewPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5.0))
                .andExpect(jsonPath("$.review_text").value("Після повторної дегустації - оцінка максимальна!"));

        // Delete review via DELETE -> 204 No Content and resets beer rating_avg to null
        mockMvc.perform(delete("/api/v1/beers/" + beerId + "/reviews")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/beers/" + beerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews_count").value(0))
                .andExpect(jsonPath("$.rating_avg").value(nullValue()));
    }

    @Test
    @DisplayName("BDD-SCN-001 / REQ-F-009: Auto-remove from Wishlist and add to Tasted Collection")
    void shouldAutoRemoveFromWishlistAndAddToTastedCollectionUponConsumptionBddScn001() throws Exception {
        JsonNode beer = createSampleBeer("Mova Dark Stout", "Mova Brewing Co.", "4820202600018");
        String beerId = beer.get("id").asText();
        String packagingId = beer.get("packagings").get(0).get("id").asText();
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokenProvider.createUserToken(userId, "user@example.com");

        // 1. Add beer to user's Wishlist -> 201 Created
        mockMvc.perform(post("/api/v1/wishlist-items/" + beerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.beer_id").value(beerId))
                .andExpect(jsonPath("$.added_at").isNotEmpty());

        // 2. Verify beer is present in GET /api/v1/wishlist-items
        mockMvc.perform(get("/api/v1/wishlist-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].beer_id").value(beerId))
                .andExpect(jsonPath("$.total_elements").value(1));

        // 3. Log consumption of 2 x 500ml cans -> auto-removes from Wishlist and adds to Collection
        String consumedAt = Instant.now().minus(Duration.ofHours(1)).toString();
        String consumptionPayload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 2,
                  "consumed_at": "%s"
                }
                """.formatted(beerId, packagingId, consumedAt);

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consumptionPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total_volume_ml").value(1000))
                .andExpect(jsonPath("$.wishlist_autoremoved").value(true))
                .andExpect(jsonPath("$.collection_added").value(true));

        // 4. Verify Wishlist is now empty (auto-cleaned)
        mockMvc.perform(get("/api/v1/wishlist-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total_elements").value(0));

        // 5. Verify beer appears in GET /api/v1/collections/tasted with total_volume_ml = 1000
        mockMvc.perform(get("/api/v1/collections/tasted")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].beer_id").value(beerId))
                .andExpect(jsonPath("$.items[0].beer_name").value("Mova Dark Stout"))
                .andExpect(jsonPath("$.items[0].brewery").value("Mova Brewing Co."))
                .andExpect(jsonPath("$.items[0].total_volume_ml").value(1000))
                .andExpect(jsonPath("$.total_elements").value(1));
    }
}
