package com.lims.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.enums.AuditAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Appends rows to the immutable {@code audit_logs} trail.
 * <p>
 * Writes go through {@link JdbcTemplate}, which reuses the JDBC connection bound to the caller's
 * transaction: the audit row commits if and only if the audited change commits. (Persisting a
 * JPA entity is not allowed while Hibernate is flushing, and a REQUIRES_NEW transaction would
 * both record changes that later roll back and take a second pooled connection mid-flush.)
 * Outside a transaction (e.g. login events) the insert auto-commits.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String INSERT_SQL = """
            INSERT INTO audit_logs (id, entity_name, entity_id, action_type, changed_by, old_data, new_data, ip_address, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordAudit(String entityName, String entityId, AuditAction action,
                            Object oldState, Object newState) {
        try {
            AuditContextHolder.AuditContext context = AuditContextHolder.getContext();
            jdbcTemplate.update(INSERT_SQL,
                    UUID.randomUUID().toString(),
                    entityName,
                    entityId,
                    action.name(),
                    context.userId(),
                    toJson(oldState),
                    toJson(newState),
                    context.ipAddress(),
                    Timestamp.from(Instant.now()));
            log.debug("Recorded audit log for {} [{}] action {}", entityName, entityId, action);
        } catch (Exception e) {
            log.error("Failed to record audit log for {} [{}]: {}", entityName, entityId, e.getMessage(), e);
        }
    }

    /**
     * Records an authentication/account event attributed to {@code username} (the caller is
     * typically still anonymous, so the audit context user would be SYSTEM).
     */
    public void recordSecurityEvent(String username, AuditAction action, Object details) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    UUID.randomUUID().toString(),
                    "AppUser",
                    username,
                    action.name(),
                    username,
                    null,
                    toJson(details),
                    AuditContextHolder.getContext().ipAddress(),
                    Timestamp.from(Instant.now()));
        } catch (Exception e) {
            log.error("Failed to record security audit event {} for {}: {}", action, username, e.getMessage(), e);
        }
    }

    private String toJson(Object state) throws Exception {
        if (state == null) {
            return null;
        }
        return state instanceof String s ? s : objectMapper.writeValueAsString(state);
    }
}
