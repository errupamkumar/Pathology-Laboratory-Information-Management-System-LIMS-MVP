package com.lims.server.web;

import com.lims.common.audit.AuditContextHolder;
import com.lims.common.security.CurrentUser;
import com.lims.common.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs after the Spring Security chain has authenticated the request and binds the user and
 * client IP to the audit context (so audit rows record who changed what) and to the MDC.
 */
public class AuditContextFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "lims.user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String user = CurrentUser.username();
        AuditContextHolder.setContext(user, request.getRemoteAddr());
        MDC.put(CorrelationId.MDC_USER_KEY, user);
        request.setAttribute(USER_ATTRIBUTE, user);
        try {
            chain.doFilter(request, response);
        } finally {
            AuditContextHolder.clear();
            MDC.remove(CorrelationId.MDC_USER_KEY);
        }
    }
}
