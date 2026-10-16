package com.lims.common.web;

/**
 * URI versioning: every controller maps under a versioned prefix so a breaking v2 can be
 * introduced side-by-side. The served version is also echoed in the {@link #VERSION_HEADER}.
 */
public final class ApiPaths {

    public static final String V1 = "/api/v1";
    public static final String VERSION_HEADER = "X-API-Version";
    public static final String CURRENT_VERSION = "1";

    private ApiPaths() {}
}
