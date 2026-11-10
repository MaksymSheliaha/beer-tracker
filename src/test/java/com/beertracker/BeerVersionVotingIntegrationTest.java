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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BeerVersionVotingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createSampleBeer() throws Exception {
        String payload = """
                {
                  "name": "Varvar IPA",
                  "brewery": "Varvar",
                  "style": "India Pale Ale",
                  "abv": 6.0,
                  "beer_type": "PACKAGED",
                  "country": "UA",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "BOTTLE",
                      "barcode": "4820199990012"
                    }
                  ]
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/beers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("REQ-F-003 / AC-003.1: POST /api/v1/beers/{id}/versions creates UNVERIFIED draft with 0 votes and lists via GET endpoints")
    void shouldCreateDraftBeerVersionAndListVersions() throws Exception {
        String beerId = createSampleBeer();
        UUID authorId = UUID.randomUUID();
        String authorToken = JwtTestTokenProvider.createUserToken(authorId, "contributor@example.com");

        String versionPayload = """
                {
                  "style": "India Pale Ale (West Coast)",
                  "packagings": [
                    {
                      "volume_ml": 330,
                      "package_type": "BOTTLE",
                      "barcode": "4820199990333"
                    }
                  ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.beer_id").value(beerId))
                .andExpect(jsonPath("$.author_id").value(authorId.toString()))
                .andExpect(jsonPath("$.payload.style").value("India Pale Ale (West Coast)"))
                .andExpect(jsonPath("$.confirm_votes").value(0))
                .andExpect(jsonPath("$.reject_votes").value(0))
                .andExpect(jsonPath("$.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andReturn();

        String versionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/beers/" + beerId + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(versionId))
                .andExpect(jsonPath("$[0].status").value("UNVERIFIED"));

        mockMvc.perform(get("/api/v1/beer-versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .param("status", "UNVERIFIED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(versionId))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    @DisplayName("REQ-F-003 / AC-003.2: Second UNVERIFIED draft for the same beer returns 409 Conflict (CONCURRENT_DRAFT_CONFLICT)")
    void shouldReturn409WhenActiveUnverifiedDraftAlreadyExists() throws Exception {
        String beerId = createSampleBeer();
        String authorToken = JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "contributor@example.com");

        String versionPayload = """
                {
                  "style": "India Pale Ale (West Coast)"
                }
                """;

        mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_DRAFT_CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("beer_id"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-004 / AC-004.1 & AC-004.2: Author self-voting returns 403 Forbidden and duplicate vote by same user returns 409 Conflict")
    void shouldForbidAuthorSelfVoteAndRejectDuplicateVoteWith409() throws Exception {
        String beerId = createSampleBeer();
        UUID authorId = UUID.randomUUID();
        String authorToken = JwtTestTokenProvider.createUserToken(authorId, "contributor@example.com");

        String versionPayload = """
                {
                  "style": "India Pale Ale (West Coast)"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String versionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        String confirmVotePayload = """
                {
                  "vote_type": "CONFIRM"
                }
                """;

        // Author attempts to vote on their own version -> 403 Forbidden
        mockMvc.perform(post("/api/v1/beer-versions/" + versionId + "/votes")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmVotePayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_VOTING_FORBIDDEN"))
                .andExpect(jsonPath("$.details[0].field").value("user_id"));

        // Distinct voter casts first vote -> 200 OK
        UUID voterId = UUID.randomUUID();
        String voterToken = JwtTestTokenProvider.createUserToken(voterId, "voter1@example.com");

        mockMvc.perform(post("/api/v1/beer-versions/" + versionId + "/votes")
                        .header("Authorization", "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmVotePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_id").value(versionId))
                .andExpect(jsonPath("$.confirm_votes").value(1))
                .andExpect(jsonPath("$.reject_votes").value(0))
                .andExpect(jsonPath("$.status").value("UNVERIFIED"));

        // Same voter attempts to vote a second time -> 409 Conflict
        mockMvc.perform(post("/api/v1/beer-versions/" + versionId + "/votes")
                        .header("Authorization", "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmVotePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VOTE_CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    @DisplayName("BDD-SCN-002 / REQ-F-005 / REQ-NF-009: Parallel voting by 5 distinct users + duplicate attempts atomically transitions version to VERIFIED")
    void shouldAutoVerifyVersionUponFiveConfirmVotesConcurrently_BddScn002() throws Exception {
        String beerId = createSampleBeer();
        UUID authorId = UUID.randomUUID();
        String authorToken = JwtTestTokenProvider.createUserToken(authorId, "contributor@example.com");

        String versionPayload = """
                {
                  "style": "India Pale Ale (West Coast)",
                  "packagings": [
                    {
                      "volume_ml": 330,
                      "package_type": "BOTTLE",
                      "barcode": "4820199990333"
                    }
                  ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String versionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        List<String> voterTokens = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            voterTokens.add(JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "voter" + i + "@example.com"));
        }
        // Add a concurrent duplicate vote from voter 1 to stress-test race condition protection (REQ-NF-009)
        voterTokens.add(voterTokens.get(0));

        CountDownLatch readyLatch = new CountDownLatch(voterTokens.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(voterTokens.size());

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (String token : voterTokens) {
            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await();
                MvcResult voteResult = mockMvc.perform(post("/api/v1/beer-versions/" + versionId + "/votes")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"vote_type\": \"CONFIRM\"}"))
                        .andReturn();
                return voteResult.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(executor.submit(task));
        }

        readyLatch.await();
        startLatch.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 200, 200, 200, 200, 409);

        // Verify version is now VERIFIED with exactly 5 confirm_votes
        mockMvc.perform(get("/api/v1/beers/" + beerId + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(versionId))
                .andExpect(jsonPath("$[0].confirm_votes").value(5))
                .andExpect(jsonPath("$[0].reject_votes").value(0))
                .andExpect(jsonPath("$[0].status").value("VERIFIED"));

        // Verify catalog returns updated v2 attributes and VERIFIED status
        mockMvc.perform(get("/api/v1/beers")
                        .param("query", "Varvar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(beerId))
                .andExpect(jsonPath("$.items[0].style").value("India Pale Ale (West Coast)"))
                .andExpect(jsonPath("$.items[0].status").value("VERIFIED"));
    }

    @Test
    @DisplayName("REQ-F-005 / AC-005.2 & AC-005.3: Reaching 5 REJECT votes transitions version to REJECTED")
    void shouldRejectVersionWhenFiveRejectVotesReached() throws Exception {
        String beerId = createSampleBeer();
        UUID authorId = UUID.randomUUID();
        String authorToken = JwtTestTokenProvider.createUserToken(authorId, "contributor@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"style\": \"Questionable Experimental Style\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String versionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        for (int i = 1; i <= 4; i++) {
            String voterToken = JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "rejecter" + i + "@example.com");
            mockMvc.perform(post("/api/v1/beer-versions/" + versionId + "/votes")
                            .header("Authorization", "Bearer " + voterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"vote_type\": \"REJECT\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reject_votes").value(i))
                    .andExpect(jsonPath("$.status").value("UNVERIFIED"));
        }

        String fifthVoterToken = JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "rejecter5@example.com");
        mockMvc.perform(post("/api/v1/beer-versions/" + versionId + "/votes")
                        .header("Authorization", "Bearer " + fifthVoterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote_type\": \"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_id").value(versionId))
                .andExpect(jsonPath("$.reject_votes").value(5))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
