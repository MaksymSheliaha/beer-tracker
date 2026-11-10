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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "trace_id";
    public static final String MDC_STATUS_KEY = "status";
    public static final String MDC_USER_ID_KEY = "user_id";
    public static final String MDC_CLIENT_IP_KEY = "client_ip";

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
            log.info("HTTP {} {} completed with status {}", request.getMethod(), request.getRequestURI(), response.getStatus());
            MDC.clear();
        }
    }
}
