package com.lims.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByEntityNameAndEntityId(String entityName, String entityId, Pageable pageable);
    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);
}
