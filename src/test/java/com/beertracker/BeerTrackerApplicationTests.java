package com.beertracker;

import com.beertracker.dto.ErrorDetail;
import com.beertracker.dto.ErrorResponse;
import com.beertracker.exception.ApiException;
import com.beertracker.exception.GlobalExceptionHandler;
import com.beertracker.filter.TraceIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BeerTrackerApplicationTests {

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void contextLoadsAndWritesStructuredJsonLog() throws Exception {
        assertThat(traceIdFilter).isNotNull();
        assertThat(globalExceptionHandler).isNotNull();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/beers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdcTraceId = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> capturedMdcTraceId.set(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY));
        traceIdFilter.doFilter(request, response, filterChain);

        String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(responseTraceId).isNotBlank();
        assertThat(capturedMdcTraceId.get()).isEqualTo(responseTraceId);

        ApiException apiException = new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNPROCESSABLE_ENTITY",
                "Validation failed for one or more fields",
                List.of(new ErrorDetail("quantity", "Quantity must be an integer between 1 and 100"))
        );
        ResponseEntity<ErrorResponse> errorEntity = globalExceptionHandler.handleApiException(apiException, request);
        assertThat(errorEntity.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ErrorResponse body = errorEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("UNPROCESSABLE_ENTITY");
        assertThat(body.message()).isEqualTo("Validation failed for one or more fields");
        assertThat(body.details()).hasSize(1);
        assertThat(body.details().getFirst().field()).isEqualTo("quantity");
        assertThat(body.details().getFirst().issue()).isEqualTo("Quantity must be an integer between 1 and 100");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.traceId()).isNotBlank();

        Path logFile = Path.of("logs/beer-tracker.json.log");
        assertThat(Files.exists(logFile)).isTrue();
        List<String> lines = Files.readAllLines(logFile);
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).startsWith("{").endsWith("}");
    }
}
