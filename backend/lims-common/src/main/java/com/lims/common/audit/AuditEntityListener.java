package com.lims.common.audit;

import com.lims.common.entity.BaseEntity;
import com.lims.common.enums.AuditAction;
import com.lims.common.util.ApplicationContextProvider;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * Registered on {@link BaseEntity}; records changes to entities annotated with {@link Auditable}.
 */
public class AuditEntityListener {

    @PostPersist
    public void onPostPersist(Object entity) {
        processAudit(entity, AuditAction.INSERT);
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        processAudit(entity, AuditAction.UPDATE);
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        processAudit(entity, AuditAction.DELETE);
    }

    private void processAudit(Object entity, AuditAction action) {
        if (!entity.getClass().isAnnotationPresent(Auditable.class)) {
            return;
        }

        AuditService auditService = ApplicationContextProvider.getBean(AuditService.class);
        if (auditService == null) {
            return;
        }

        String entityName = entity.getClass().getSimpleName();
        String entityId = "UNKNOWN";
        if (entity instanceof BaseEntity baseEntity && baseEntity.getId() != null) {
            entityId = baseEntity.getId().toString();
        }

        auditService.recordAudit(entityName, entityId, action, null, AuditSnapshot.of(entity));
    }
}
