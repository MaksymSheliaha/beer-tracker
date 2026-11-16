package com.beertracker.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter executed at highest precedence to initialize SLF4J MDC fields
 * ({@code trace_id}, {@code client_ip}, {@code user_id}, {@code status}) and attach
 * {@code X-Trace-Id} to every HTTP response ({@code REQ-NF-010}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * HTTP header carrying the distributed request correlation UUID.
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * MDC key holding the request correlation UUID.
     */
    public static final String MDC_TRACE_ID_KEY = "trace_id";

    /**
     * MDC key holding the final HTTP status code.
     */
    public static final String MDC_STATUS_KEY = "status";

    /**
     * MDC key holding the authenticated user UUID or {@code anonymous}.
     */
    public static final String MDC_USER_ID_KEY = "user_id";

    /**
     * MDC key holding the client remote IP address.
     */
    public static final String MDC_CLIENT_IP_KEY = "client_ip";

    private static final Logger LOG = LoggerFactory.getLogger(TraceIdFilter.class);

    /**
     * Populates request-scoped MDC diagnostic attributes, propagates {@code X-Trace-Id}
     * on the HTTP response, logs completion status, and clears MDC upon return.
     *
     * @param request     incoming HTTP servlet request
     * @param response    outgoing HTTP servlet response
     * @param filterChain remaining servlet filter chain
     * @throws ServletException if downstream request processing fails
     * @throws IOException      if an I/O error occurs while handling the request
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_TRACE_ID_KEY, traceId);
        MDC.put(MDC_CLIENT_IP_KEY, request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1");
        if (MDC.get(MDC_USER_ID_KEY) == null) {
            MDC.put(MDC_USER_ID_KEY, "anonymous");
        }
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.put(MDC_STATUS_KEY, String.valueOf(response.getStatus()));
            LOG.info(
                    "HTTP {} {} completed with status {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus()
            );
            MDC.clear();
        }
    }
}
