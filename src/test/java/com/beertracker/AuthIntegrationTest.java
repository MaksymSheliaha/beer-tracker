package com.beertracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("REQ-NF-004 / REQ-NF-006: Register creates USER with 201 and omits password_hash")
    void shouldRegisterUserWith201AndNeverLeakPasswordHash() throws Exception {
        String registerPayload = """
                {
                  "email": "user@example.com",
                  "password": "SuperSecretPassword123!",
                  "timezone": "Europe/Kyiv"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.timezone").value("Europe/Kyiv"))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("SuperSecretPassword123!", "password_hash", "$2a$", "$2b$");
    }

    @Test
    @DisplayName("REQ-NF-004: Register with duplicate email returns 409 EMAIL_ALREADY_EXISTS")
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        String registerPayload = """
                {
                  "email": "duplicate@example.com",
                  "password": "SuperSecretPassword123!",
                  "timezone": "Europe/Kyiv"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-NF-004 / REQ-NF-008: Register with invalid timezone or short password returns 422")
    void shouldReturn422OnInvalidTimezoneOrShortPassword() throws Exception {
        String invalidPayload = """
                {
                  "email": "invalid@example.com",
                  "password": "short",
                  "timezone": "Invalid/Timezone_Name"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("API-POS-012 / REQ-NF-004 / REQ-NF-006: Login validates BCrypt(12) and returns TokenResponse")
    void shouldAuthenticateWithBCryptAndGenerateAccessAndRefreshTokensApiPos012() throws Exception {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String sampleHash = encoder.encode("SuperSecretPassword123!");
        assertThat(sampleHash).matches("^\\$2[aby]\\$12\\$.+");
        assertThat(encoder.matches("SuperSecretPassword123!", sampleHash)).isTrue();

        String registerPayload = """
                {
                  "email": "user@example.com",
                  "password": "SuperSecretPassword123!",
                  "timezone": "Europe/Kyiv"
                }
                """;

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String userId = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("id").asText();

        String loginPayload = """
                {
                  "email": "user@example.com",
                  "password": "SuperSecretPassword123!"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andReturn();

        JsonNode tokenJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = tokenJson.get("access_token").asText();
        String[] jwtParts = accessToken.split("\\.");
        assertThat(jwtParts).hasSize(3);

        String payloadJson = new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);
        JsonNode claims = objectMapper.readTree(payloadJson);
        assertThat(claims.get("sub").asText()).isEqualTo(userId);
        assertThat(claims.get("role").asText()).isEqualTo("USER");
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(900L);
    }

    @Test
    @DisplayName("API-NEG-005 / REQ-NF-004 / REQ-NF-006: Login with wrong password returns 401 INVALID_CREDENTIALS")
    void shouldReturn401OnInvalidCredentialsApiNeg005() throws Exception {
        String registerPayload = """
                {
                  "email": "user@example.com",
                  "password": "SuperSecretPassword123!",
                  "timezone": "Europe/Kyiv"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String wrongPasswordPayload = """
                {
                  "email": "user@example.com",
                  "password": "WrongPassword999!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.details[0].field").value("credentials"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("REQ-NF-004: Refresh rotates refresh_token once (200) and rejects reused token (401)")
    void shouldRotateRefreshTokenAndRejectReusedTokenWith401() throws Exception {
        String registerPayload = """
                {
                  "email": "rotate@example.com",
                  "password": "SuperSecretPassword123!",
                  "timezone": "Europe/Kyiv"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String loginPayload = """
                {
                  "email": "rotate@example.com",
                  "password": "SuperSecretPassword123!"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        String initialRefreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refresh_token").asText();

        String refreshRequestPayload = """
                {
                  "refresh_token": "%s"
                }
                """.formatted(initialRefreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequestPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andReturn();

        String rotatedRefreshToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("refresh_token").asText();
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequestPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.details[0].field").value("refresh_token"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
