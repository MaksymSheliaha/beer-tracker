package com.beertracker;

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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BeerCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userAuthHeader() {
        return "Bearer " + JwtTestTokenProvider.createUserToken(UUID.randomUUID(), "catalog@example.com");
    }

    @Test
    @DisplayName("REQ-F-001 / AC-001.1: POST /api/v1/beers creates UNVERIFIED beer with 201")
    void shouldCreateBeerWithUnverifiedStatusAndReturn201() throws Exception {
        String payload = """
                {
                  "name": "Mova Dark Stout",
                  "brewery": "Mova Brewing Co.",
                  "style": "Oatmeal Stout",
                  "abv": 5.8,
                  "beer_type": "PACKAGED",
                  "description": "Насичений темний стаут з вівсяними пластівцями",
                  "country": "UA",
                  "image_url": "https://cdn.beertracker.local/beers/mova-stout.png",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "CAN",
                      "barcode": "4820202600018"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", userAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Mova Dark Stout"))
                .andExpect(jsonPath("$.brewery").value("Mova Brewing Co."))
                .andExpect(jsonPath("$.style").value("Oatmeal Stout"))
                .andExpect(jsonPath("$.abv").value(5.8))
                .andExpect(jsonPath("$.beer_type").value("PACKAGED"))
                .andExpect(jsonPath("$.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.packagings").isArray())
                .andExpect(jsonPath("$.packagings[0].id").isNotEmpty())
                .andExpect(jsonPath("$.packagings[0].volume_ml").value(500))
                .andExpect(jsonPath("$.packagings[0].package_type").value("CAN"))
                .andExpect(jsonPath("$.packagings[0].barcode").value("4820202600018"))
                .andExpect(jsonPath("$.created_at").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-NF-004: POST /api/v1/beers without Authorization returns 401 UNAUTHORIZED")
    void shouldReturn401WhenCreatingBeerWithoutAuthentication() throws Exception {
        String payload = """
                {
                  "name": "Unauthorized Ale",
                  "brewery": "Varvar",
                  "style": "Pale Ale",
                  "abv": 5.0,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "CAN"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-001: GET /api/v1/beers returns 200 OK with BeerPagedResponse structure")
    void shouldListBeersWithPaginationAndReturn200() throws Exception {
        String payload = """
                {
                  "name": "Varvar Artisan IPA",
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

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", userAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/beers")
                        .param("query", "Varvar")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].name").value("Varvar Artisan IPA"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.has_next").value(false))
                .andExpect(jsonPath("$.has_previous").value(false));
    }

    @Test
    @DisplayName("REQ-NF-008: GET /api/v1/beers with size > 100 returns 400 INVALID_PAGE_SIZE")
    void shouldReturn400WhenPageSizeExceedsMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/beers")
                        .param("page", "0")
                        .param("size", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"))
                .andExpect(jsonPath("$.details[0].field").value("size"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-001 / AC-001.2: Packaging volume < 50 ml returns 422 VOLUME_OUT_OF_BOUNDS")
    void shouldReturn422WhenPackagingVolumeIsOutOfBounds() throws Exception {
        String payload = """
                {
                  "name": "Tiny Sample Ale",
                  "brewery": "Sample Brewery",
                  "style": "Pale Ale",
                  "abv": 4.5,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 30,
                      "package_type": "BOTTLE"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", userAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOLUME_OUT_OF_BOUNDS"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0].field").value("packagings[0].volume_ml"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-001 / AC-001.3: DRAFT beer with BOTTLE packaging returns 422")
    void shouldReturn422WhenDraftBeerHasBottlePackaging() throws Exception {
        String draftWithBottlePayload = """
                {
                  "name": "Varvar Golden Ale On Tap",
                  "brewery": "Varvar",
                  "style": "Golden Ale",
                  "abv": 5.0,
                  "beer_type": "DRAFT",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "BOTTLE"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", userAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftWithBottlePayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PACKAGE_TYPE_FOR_DRAFT"))
                .andExpect(jsonPath("$.details[0].field").value("packagings[0].package_type"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-001 / AC-001.4: PACKAGED beer with DRAFT packaging returns 422")
    void shouldReturn422WhenPackagedBeerHasDraftPackaging() throws Exception {
        String packagedWithDraftPayload = """
                {
                  "name": "Varvar Packaged Wrongly",
                  "brewery": "Varvar",
                  "style": "Golden Ale",
                  "abv": 5.0,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "DRAFT"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", userAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(packagedWithDraftPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.details[0].field").value("packagings[0].package_type"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("BDD-SCN-004 / REQ-F-002 / AC-002.2: Duplicate barcode returns 409 Conflict")
    void shouldReturn409OnDuplicateBarcodeBddScn004() throws Exception {
        String authHeader = userAuthHeader();
        String existingBeerPayload = """
                {
                  "name": "Pilsner Urquell",
                  "brewery": "Plzensky Prazdroj",
                  "style": "Pilsner",
                  "abv": 4.4,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "BOTTLE",
                      "barcode": "8594404110123"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(existingBeerPayload))
                .andExpect(status().isCreated());

        String duplicateBarcodePayload = """
                {
                  "name": "Urquell Original",
                  "brewery": "Plzensky Prazdroj",
                  "style": "Pilsner",
                  "abv": 4.4,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "BOTTLE",
                      "barcode": "8594404110123"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateBarcodePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BARCODE_CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("packagings[0].barcode"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-002 / AC-002.1: Case-insensitive duplicate (brewery, name) returns 409")
    void shouldReturn409OnDuplicateBreweryAndNameCaseInsensitive() throws Exception {
        String authHeader = userAuthHeader();
        String firstBeerPayload = """
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

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBeerPayload))
                .andExpect(status().isCreated());

        String duplicateNamePayload = """
                {
                  "name": "  mova dark stout  ",
                  "brewery": "  MOVA BREWING CO. ",
                  "style": "Oatmeal Stout",
                  "abv": 5.8,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 330,
                      "package_type": "CAN",
                      "barcode": "4820202600099"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateNamePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BEER_NAME_CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("BDD-SCN-006 / REQ-F-015: Filter by min_abv/max_abv and reject inverted range")
    void shouldFilterBeersByAbvRangeAndRejectInvalidRangeBddScn006() throws Exception {
        String authHeader = userAuthHeader();
        String lightBeer = """
                {
                  "name": "Light Session IPA",
                  "brewery": "Varvar",
                  "style": "Session IPA",
                  "abv": 3.8,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 330,
                      "package_type": "CAN",
                      "barcode": "4820777000011"
                    }
                  ]
                }
                """;
        String mediumBeer = """
                {
                  "name": "Mova Classic Stout",
                  "brewery": "Mova Brewing Co.",
                  "style": "Stout",
                  "abv": 5.8,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 500,
                      "package_type": "CAN",
                      "barcode": "4820777000012"
                    }
                  ]
                }
                """;
        String strongBeer = """
                {
                  "name": "Imperial Baltic Porter",
                  "brewery": "Pravda",
                  "style": "Baltic Porter",
                  "abv": 9.5,
                  "beer_type": "PACKAGED",
                  "packagings": [
                    {
                      "volume_ml": 330,
                      "package_type": "BOTTLE",
                      "barcode": "4820777000013"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lightBeer))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mediumBeer))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(strongBeer))
                .andExpect(status().isCreated());

        // AC-015.1: Filter by [5.0 .. 8.0] -> returns only Mova Classic Stout (abv 5.8)
        mockMvc.perform(get("/api/v1/beers")
                        .param("min_abv", "5.0")
                        .param("max_abv", "8.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Mova Classic Stout"))
                .andExpect(jsonPath("$.items[0].abv").value(5.8));

        // AC-015.2: Inverted range min_abv > max_abv -> 400 Bad Request (INVALID_ABV_RANGE)
        mockMvc.perform(get("/api/v1/beers")
                        .param("min_abv", "8.0")
                        .param("max_abv", "4.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ABV_RANGE"));
    }

    @Test
    @DisplayName("REQ-F-003 / AC-003.3: GET /beers/{id} supports verified_only and returns 404")
    void shouldGetBeerByIdWithVerifiedOnlyOptionAndReturn404WhenMissing() throws Exception {
        String authHeader = userAuthHeader();
        String payload = """
                {
                  "name": "Varvar Artisan IPA",
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

        MvcResult createdResult = mockMvc.perform(post("/api/v1/beers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String beerId = objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/beers/" + beerId + "/versions")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"style\": \"India Pale Ale (West Coast)\"}"))
                .andExpect(status().isCreated());

        // Default (verified_only=false) returns latest proposed draft attributes
        mockMvc.perform(get("/api/v1/beers/" + beerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(beerId))
                .andExpect(jsonPath("$.style").value("India Pale Ale (West Coast)"));

        // verified_only=true returns original un-overlaid catalog attributes
        mockMvc.perform(get("/api/v1/beers/" + beerId)
                        .param("verified_only", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(beerId))
                .andExpect(jsonPath("$.style").value("India Pale Ale"));

        // Non-existent ID returns 404 BEER_NOT_FOUND
        mockMvc.perform(get("/api/v1/beers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BEER_NOT_FOUND"));
    }
}
