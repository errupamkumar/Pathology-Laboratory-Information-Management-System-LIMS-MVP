package com.lims.common.security;

import java.util.List;

/**
 * Role catalogue and the access matrix used by {@code @PreAuthorize} across all modules.
 * Keeping the SpEL expressions here means the matrix is defined once and reviewed in one place.
 *
 * <pre>
 *                      ADMIN  PATHOLOGIST  TECHNICIAN  RECEPTIONIST
 * Register / bill        x                                x
 * View clinical data     x         x            x         x
 * Receive / reject       x                      x
 * Enter results          x         x            x
 * Authorize report                 x
 * Manage users           x
 * </pre>
 *
 * Report authorization is deliberately PATHOLOGIST-only (ISO 15189 §7.4.1.1): an administrator
 * may only sign off if they also hold the pathologist role.
 */
public final class Roles {

    public static final String PREFIX = "ROLE_";

    public static final String ADMIN = "ADMIN";
    public static final String PATHOLOGIST = "PATHOLOGIST";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String RECEPTIONIST = "RECEPTIONIST";

    public static final List<String> ALL = List.of(ADMIN, PATHOLOGIST, TECHNICIAN, RECEPTIONIST);

    public static final String CAN_REGISTER = "hasAnyRole('ADMIN','RECEPTIONIST')";
    public static final String CAN_VIEW_CLINICAL = "hasAnyRole('ADMIN','PATHOLOGIST','TECHNICIAN','RECEPTIONIST')";
    public static final String CAN_ACCESSION = "hasAnyRole('ADMIN','TECHNICIAN')";
    public static final String CAN_ENTER_RESULTS = "hasAnyRole('ADMIN','PATHOLOGIST','TECHNICIAN')";
    public static final String CAN_AUTHORIZE = "hasRole('PATHOLOGIST')";
    public static final String IS_ADMIN = "hasRole('ADMIN')";

    private Roles() {}

    public static String authority(String role) {
        return role.startsWith(PREFIX) ? role : PREFIX + role;
    }
}
