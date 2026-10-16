package com.lims.common.web;

import org.slf4j.MDC;

/**
 * Request correlation identifier shared by the logging filter, async executors and error responses.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String MDC_USER_KEY = "user";

    private CorrelationId() {}

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
