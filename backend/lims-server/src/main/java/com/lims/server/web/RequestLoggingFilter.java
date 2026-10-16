package com.lims.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * One access-log line per API request: method, route template, status, latency and user.
 * <p>
 * PHI safeguard: logs the matched route template ({@code /api/v1/patients/phone/{phone}}), never
 * the raw URI or query string, and never request/response bodies.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("lims.access");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            Object user = request.getAttribute(AuditContextFilter.USER_ATTRIBUTE);
            int status = response.getStatus();
            String message = "{} {} -> {} ({} ms) user={}";
            Object[] args = {request.getMethod(), route != null ? route : "[unmatched]", status, millis, user != null ? user : "anonymous"};
            if (status >= 500) {
                log.error(message, args);
            } else if (status >= 400) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        }
    }
}
