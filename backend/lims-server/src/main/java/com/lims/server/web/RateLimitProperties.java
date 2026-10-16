package com.lims.server.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param loginRequestsPerMinute  sign-in attempts per client IP (credential-stuffing guard)
 * @param apiRequestsPerMinute    API calls per signed-in user (or per IP when anonymous)
 */
@ConfigurationProperties("lims.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int loginRequestsPerMinute,
        @DefaultValue("300") int apiRequestsPerMinute
) {}
