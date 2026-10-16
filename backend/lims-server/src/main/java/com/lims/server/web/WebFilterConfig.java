package com.lims.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Servlet filter ordering relative to the Spring Security chain (order -100):
 * <pre>
 *  correlation id (first) -> access log -> [security] -> audit context -> rate limit -> ETag
 * </pre>
 */
@Configuration
public class WebFilterConfig {

    private static final int AFTER_SECURITY = SecurityProperties.DEFAULT_FILTER_ORDER + 10;

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuditContextFilter> auditContextFilter() {
        FilterRegistrationBean<AuditContextFilter> registration = new FilterRegistrationBean<>(new AuditContextFilter());
        registration.setOrder(AFTER_SECURITY);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitingFilter> registration =
                new FilterRegistrationBean<>(new RateLimitingFilter(properties, objectMapper));
        registration.setOrder(AFTER_SECURITY + 10);
        return registration;
    }

    /**
     * Conditional GET for reference data: catalog responses get an ETag and a matching
     * {@code If-None-Match} is answered with 304 and no body.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> catalogEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/v1/catalog/*");
        registration.setOrder(AFTER_SECURITY + 20);
        return registration;
    }
}
