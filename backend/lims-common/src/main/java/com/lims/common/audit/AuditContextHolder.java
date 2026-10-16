package com.lims.common.audit;

public final class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CONTEXT = ThreadLocal.withInitial(
            () -> new AuditContext("SYSTEM", "127.0.0.1")
    );

    private AuditContextHolder() {}

    public static void setContext(String userId, String ipAddress) {
        CONTEXT.set(new AuditContext(
                userId != null ? userId : "SYSTEM",
                ipAddress != null ? ipAddress : "127.0.0.1"
        ));
    }

    public static AuditContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public record AuditContext(String userId, String ipAddress) {}
}
