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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AnalyticsAndStreaksIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("REQ-F-012 (AC-012.1, AC-012.2): Continuous streak across consecutive days in user's IANA timezone [00:00, 24:00)")
    void shouldCalculateContinuousStreakWithTimezoneDayBoundaries() throws Exception {
        ZoneId kyivZone = ZoneId.of("Europe/Kyiv");
        String token = registerAndLoginUser("streak.active@example.com", "Europe/Kyiv");
        String[] ids = createBeerAndGetIds("Mova Hoppy Lager", "Mova Brewing Co.", "4820888000101");
        String beerId = ids[0];
        String packagingId = ids[1];

        LocalDate todayKyiv = LocalDate.now(kyivZone);
        // Log consumption 2 days ago at 23:55 Kyiv time, yesterday at 00:05 Kyiv time (just after midnight boundary), and today at 12:00 Kyiv time
        ZonedDateTime twoDaysAgoLate = todayKyiv.minusDays(2).atTime(23, 55).atZone(kyivZone);
        ZonedDateTime yesterdayJustAfterMidnight = todayKyiv.minusDays(1).atTime(0, 5).atZone(kyivZone);
        ZonedDateTime todayEarly = todayKyiv.atTime(0, 1).atZone(kyivZone);

        logConsumption(token, beerId, packagingId, 1, twoDaysAgoLate.toInstant().toString());
        logConsumption(token, beerId, packagingId, 2, yesterdayJustAfterMidnight.toInstant().toString());
        logConsumption(token, beerId, packagingId, 1, todayEarly.toInstant().toString());

        mockMvc.perform(get("/api/v1/streaks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_streak").value(3))
                .andExpect(jsonPath("$.longest_streak").value(3))
                .andExpect(jsonPath("$.last_consumed_date").value(todayKyiv.toString()))
                .andExpect(jsonPath("$.is_active_today").value(true));
    }

    @Test
    @DisplayName("REQ-F-012 (AC-012.2): Streak resets to 0 when a day is skipped, while preserving longest_streak")
    void shouldResetCurrentStreakWhenDayIsSkippedAndPreserveLongestStreak() throws Exception {
        ZoneId kyivZone = ZoneId.of("Europe/Kyiv");
        String token = registerAndLoginUser("streak.broken@example.com", "Europe/Kyiv");
        String[] ids = createBeerAndGetIds("Varvar Golden Ale", "Varvar Brew", "4820888000102");
        String beerId = ids[0];
        String packagingId = ids[1];

        LocalDate todayKyiv = LocalDate.now(kyivZone);
        // 4 consecutive days ending 3 days ago (so yesterday and 2 days ago were skipped -> current_streak = 0, longest_streak = 4)
        for (int daysAgo = 6; daysAgo >= 3; daysAgo--) {
            ZonedDateTime timestamp = todayKyiv.minusDays(daysAgo).atTime(19, 30).atZone(kyivZone);
            logConsumption(token, beerId, packagingId, 1, timestamp.toInstant().toString());
        }

        mockMvc.perform(get("/api/v1/streaks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_streak").value(0))
                .andExpect(jsonPath("$.longest_streak").value(4))
                .andExpect(jsonPath("$.last_consumed_date").value(todayKyiv.minusDays(3).toString()))
                .andExpect(jsonPath("$.is_active_today").value(false));
    }

    @Test
    @DisplayName("REQ-F-012 (AC-012.3, AC-012.4): Hourly consumption profile and period aggregation")
    void shouldCalculateHourlyConsumptionProfileAndPeriodAggregation() throws Exception {
        ZoneId kyivZone = ZoneId.of("Europe/Kyiv");
        String token = registerAndLoginUser("analytics.hourly@example.com", "Europe/Kyiv");
        String[] ids = createBeerAndGetIds("Underwood Amber Ale", "Underwood Brewery", "4820888000103");
        String beerId = ids[0];
        String packagingId = ids[1]; // 500 ml

        LocalDate todayKyiv = LocalDate.now(kyivZone);
        LocalDate day1 = todayKyiv.minusDays(2);
        LocalDate day2 = todayKyiv.minusDays(1);

        // Day 1: 19:15 (2 * 500 = 1000 ml), 20:10 (1 * 500 = 500 ml)
        logConsumption(token, beerId, packagingId, 2, day1.atTime(19, 15).atZone(kyivZone).toInstant().toString());
        logConsumption(token, beerId, packagingId, 1, day1.atTime(20, 10).atZone(kyivZone).toInstant().toString());

        // Day 2: 19:45 (1 * 500 = 500 ml)
        logConsumption(token, beerId, packagingId, 1, day2.atTime(19, 45).atZone(kyivZone).toInstant().toString());

        // Total active days = 2 (day1 and day2)
        // Hour 19: total volume = 1500 ml, entry_count = 2, average_volume_ml = 1500 / 2 = 750.0
        // Hour 20: total volume = 500 ml, entry_count = 1, average_volume_ml = 500 / 2 = 250.0
        mockMvc.perform(get("/api/v1/analytics/consumption")
                        .header("Authorization", "Bearer " + token)
                        .param("date_from", day1.toString())
                        .param("date_to", day2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Kyiv"))
                .andExpect(jsonPath("$.current_streak").value(2))
                .andExpect(jsonPath("$.longest_streak").value(2))
                .andExpect(jsonPath("$.total_volume_ml").value(2000))
                .andExpect(jsonPath("$.total_entries").value(3))
                .andExpect(jsonPath("$.hourly_profile", hasSize(24)))
                .andExpect(jsonPath("$.hourly_profile[19].hour").value(19))
                .andExpect(jsonPath("$.hourly_profile[19].volume_ml").value(1500))
                .andExpect(jsonPath("$.hourly_profile[19].entry_count").value(2))
                .andExpect(jsonPath("$.hourly_profile[19].average_volume_ml").value(750.0))
                .andExpect(jsonPath("$.hourly_profile[20].hour").value(20))
                .andExpect(jsonPath("$.hourly_profile[20].volume_ml").value(500))
                .andExpect(jsonPath("$.hourly_profile[20].entry_count").value(1))
                .andExpect(jsonPath("$.hourly_profile[20].average_volume_ml").value(250.0));
    }

    private void logConsumption(String token, String beerId, String packagingId, int quantity, String consumedAtIso) throws Exception {
        String payload = """
                {
                  "beer_id": "%s",
                  "packaging_id": "%s",
                  "quantity": %d,
                  "consumed_at": "%s"
                }
                """.formatted(beerId, packagingId, quantity, consumedAtIso);

        mockMvc.perform(post("/api/v1/consumption-entries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    private String registerAndLoginUser(String email, String timezone) throws Exception {
        String registerPayload = """
                {
                  "email": "%s",
                  "password": "StrongPassword123!",
                  "timezone": "%s"
                }
                """.formatted(email, timezone);

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
        String beerPayload = """
                {
                  "name": "%s",
                  "brewery": "%s",
                  "style": "Lager",
                  "abv": 5.0,
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
