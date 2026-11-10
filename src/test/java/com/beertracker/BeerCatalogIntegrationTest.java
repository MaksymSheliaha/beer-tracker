package com.beertracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    @DisplayName("REQ-F-001 / AC-001.1: POST /api/v1/beers creates product with UNVERIFIED status and returns 201 Created")
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
    @DisplayName("REQ-NF-008: GET /api/v1/beers with size > 100 returns 400 Bad Request (INVALID_PAGE_SIZE)")
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
    @DisplayName("REQ-F-001 / AC-001.2: POST /api/v1/beers with packaging volume < 50 ml returns 422 Unprocessable Entity")
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
    @DisplayName("REQ-F-001 / AC-001.3 & AC-001.4: POST /api/v1/beers with incompatible beer_type and package_type returns 422")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftWithBottlePayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PACKAGE_TYPE_FOR_DRAFT"))
                .andExpect(jsonPath("$.details[0].field").value("packagings[0].package_type"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("BDD-SCN-004 / REQ-F-002 / AC-002.2: Duplicate barcode registration returns 409 Conflict")
    void shouldReturn409OnDuplicateBarcode_BddScn004() throws Exception {
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateBarcodePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BARCODE_CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("packagings[0].barcode"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-F-002 / AC-002.1: Case-insensitive duplicate (brewery, name) returns 409 Conflict")
    void shouldReturn409OnDuplicateBreweryAndNameCaseInsensitive() throws Exception {
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateNamePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BEER_NAME_CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("BDD-SCN-006 / REQ-F-015 (SRS-ADD-002): GET /api/v1/beers filters by min_abv and max_abv and rejects invalid ABV range")
    void shouldFilterBeersByAbvRangeAndRejectInvalidRange_BddScn006() throws Exception {
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

        mockMvc.perform(post("/api/v1/beers").contentType(MediaType.APPLICATION_JSON).content(lightBeer))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/beers").contentType(MediaType.APPLICATION_JSON).content(mediumBeer))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/beers").contentType(MediaType.APPLICATION_JSON).content(strongBeer))
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
}
