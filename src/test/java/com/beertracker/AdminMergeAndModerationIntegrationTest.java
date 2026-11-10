package com.beertracker;

import com.beertracker.security.JwtTokenProvider;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminMergeAndModerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("REQ-F-010: Soft Delete by MODERATOR sets PLACEHOLDER status, hides from catalog & wishlist, and shows 'Видалений продукт' in consumption log")
    void shouldSoftDeleteBeerAndPreserveConsumptionHistoryWithPlaceholderName() throws Exception {
        String userToken = registerAndLoginUser("consumer.softdelete@example.com");
        String moderatorToken = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), "moderator@beertracker.local", "MODERATOR"
        );

        String[] ids = createBeerAndGetIds("Pravda Lviv Red Ale", "Pravda Beer Theatre", "4820999000101");
        String beerId = ids[0];
        String packagingId = ids[1];

        // Regular USER cannot delete beer (403 Forbidden)
        mockMvc.perform(delete("/api/v1/beers/" + beerId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Consumer logs consumption of the beer
        String consumedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
        String consumptionJson = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": 2,
                  "consumed_at": "%s"
                }
                """.formatted(beerId, packagingId, consumedAt);

        MvcResult logRes = mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consumptionJson))
                .andExpect(status().isCreated())
                .andReturn();
        String entryId = objectMapper.readTree(logRes.getResponse().getContentAsString()).get("id").asText();

        // MODERATOR performs Soft Delete -> 204 No Content
        mockMvc.perform(delete("/api/v1/beers/" + beerId)
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isNoContent());

        // Deleted beer disappears from public catalog search
        mockMvc.perform(get("/api/v1/beers").param("query", "Pravda Lviv Red Ale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        // Consumption history preserves entry & volume, displaying "Видалений продукт"
        mockMvc.perform(get("/api/v1/consumption-entries/" + entryId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beer_name").value("Видалений продукт"))
                .andExpect(jsonPath("$.total_volume_ml").value(1000));
    }

    @Test
    @DisplayName("REQ-F-011 / REQ-NF-007: Transactional Merge migrates packagings, consumption entries, deduplicates reviews, and rolls back on self-merge")
    void shouldMergeDuplicateBeerIntoTargetAndMigrateForeignKeys() throws Exception {
        String userToken = registerAndLoginUser("consumer.merge@example.com");
        String moderatorToken = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), "moderator@beertracker.local", "MODERATOR"
        );

        // Create Target Beer (500ml CAN) and Source Duplicate Beer (330ml BOTTLE)
        String[] targetIds = createBeerWithCustomPackaging("Varvar Milk Stout", "Varvar Brew", 500, "CAN", "4820999000201");
        String targetBeerId = targetIds[0];
        String targetPkgId = targetIds[1];

        String[] sourceIds = createBeerWithCustomPackaging("Varvar Milk Stout Duplicate", "Varvar Brew", 330, "BOTTLE", "4820999000202");
        String sourceBeerId = sourceIds[0];
        String sourcePkgId = sourceIds[1];

        String consumedAt = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();

        // User consumes both target and source beer
        mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "beer_id": "%s",
                                  "packaging_id": "%s",
                                  "quantity": 1,
                                  "consumed_at": "%s"
                                }
                                """.formatted(targetBeerId, targetPkgId, consumedAt)))
                .andExpect(status().isCreated());

        MvcResult sourceConsumptionRes = mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "beer_id": "%s",
                                  "packaging_id": "%s",
                                  "quantity": 2,
                                  "consumed_at": "%s"
                                }
                                """.formatted(sourceBeerId, sourcePkgId, consumedAt)))
                .andExpect(status().isCreated())
                .andReturn();
        String sourceEntryId = objectMapper.readTree(sourceConsumptionRes.getResponse().getContentAsString()).get("id").asText();

        // User leaves an older review on targetBeer (rating 4.0) and a newer review on sourceBeer (rating 5.0)
        mockMvc.perform(post("/api/v1/beers/" + targetBeerId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 4.0,
                                  "review_text": "Older review on target"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/beers/" + sourceBeerId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5.0,
                                  "review_text": "Newer review on duplicate source"
                                }
                                """))
                .andExpect(status().isCreated());

        // Self-merge attempt must fail with 422 CANNOT_MERGE_BEER_INTO_ITSELF and leave state intact
        mockMvc.perform(post("/api/v1/merges")
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source_beer_id": "%s",
                                  "target_beer_id": "%s"
                                }
                                """.formatted(sourceBeerId, sourceBeerId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_MERGE_BEER_INTO_ITSELF"));

        // Execute valid merge: sourceBeerId -> targetBeerId
        mockMvc.perform(post("/api/v1/merges")
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source_beer_id": "%s",
                                  "target_beer_id": "%s"
                                }
                                """.formatted(sourceBeerId, targetBeerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target_beer_id").value(targetBeerId))
                .andExpect(jsonPath("$.migrated_consumptions_count").value(1))
                .andExpect(jsonPath("$.migrated_packagings_count").value(1))
                .andExpect(jsonPath("$.archived_reviews_count").value(1))
                .andExpect(jsonPath("$.status").value("MERGED"));

        // Verify source consumption entry now points to targetBeerId
        mockMvc.perform(get("/api/v1/consumption-entries/" + sourceEntryId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beer_id").value(targetBeerId))
                .andExpect(jsonPath("$.beer_name").value("Varvar Milk Stout"));

        // Verify user's tasted collection has 1 combined entry for targetBeerId with total_volume_ml = 500 + 660 = 1160 and rating 5.0
        mockMvc.perform(get("/api/v1/collections/tasted")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].beer_id").value(targetBeerId))
                .andExpect(jsonPath("$.items[0].total_volume_ml").value(1160))
                .andExpect(jsonPath("$.items[0].user_rating").value(5.0));
    }

    @Test
    @DisplayName("REQ-F-013 & REQ-F-014: Moderator can manually verify/reject versions and process moderation queue decisions")
    void shouldVerifyAndRejectVersionsAndProcessModerationQueue() throws Exception {
        String authorToken = registerAndLoginUser("author.mod@example.com");
        String moderatorToken = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), "moderator@beertracker.local", "MODERATOR"
        );

        String[] ids = createBeerAndGetIds("Suspicious Draft Beer", "Unknown Brew", "4820999000301");
        String beerId = ids[0];

        // Author creates a version draft v1
        MvcResult v1Res = mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "style": "Imperial Stout"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String v1Id = objectMapper.readTree(v1Res.getResponse().getContentAsString()).get("id").asText();

        // Regular user cannot call moderator reject endpoint (403)
        mockMvc.perform(post("/api/v1/beer-versions/" + v1Id + "/reject")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Invalid style"
                                }
                                """))
                .andExpect(status().isForbidden());

        // Moderator rejects v1 -> status becomes REJECTED, and unverified beer enters moderation queue (REJECTED)
        mockMvc.perform(post("/api/v1/beer-versions/" + v1Id + "/reject")
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Unconfirmed data"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v1Id))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Moderator inspects moderation queue -> sees the rejected beer
        mockMvc.perform(get("/api/v1/moderation-items/queue")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(beerId))
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"));

        // Moderator decides to APPROVE the beer from the moderation queue -> status becomes VERIFIED
        mockMvc.perform(post("/api/v1/moderation-items/" + beerId + "/decision")
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "Verified manually with brewery catalog"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beer_id").value(beerId))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.processed_at").isNotEmpty());

        // Moderation queue is now empty
        mockMvc.perform(get("/api/v1/moderation-items/queue")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        // Author proposes a new version v2, and Moderator immediately verifies it
        MvcResult v2Res = mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "style": "Verified Baltic Porter"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String v2Id = objectMapper.readTree(v2Res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/beer-versions/" + v2Id + "/verify")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v2Id))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    private String registerAndLoginUser(String email) throws Exception {
        String registerPayload = """
                {
                  "email": "%s",
                  "password": "StrongPassword123!",
                  "timezone": "Europe/Kyiv"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String loginPayload = """
                {
                  "email": "%s",
                  "password": "StrongPassword123!"
                }
                """.formatted(email);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return node.get("access_token").asText();
    }

    private String[] createBeerAndGetIds(String name, String brewery, String barcode) throws Exception {
        return createBeerWithCustomPackaging(name, brewery, 500, "CAN", barcode);
    }

    private String[] createBeerWithCustomPackaging(
            String name,
            String brewery,
            int volumeMl,
            String packageType,
            String barcode
    ) throws Exception {
        String beerPayload = """
                {
                  "name": "%s",
                  "brewery": "%s",
                  "style": "Stout",
                  "abv": 6.0,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": %d,
                      "package_type": "%s",
                      "barcode": "%s"
                    }
                  ]
                }
                """.formatted(name, brewery, volumeMl, packageType, barcode);

        MvcResult res = mockMvc.perform(post("/api/v1/beers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(beerPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return new String[]{
                root.get("id").asText(),
                root.get("packagings").get(0).get("id").asText()
        };
    }
}
