package com.lims.common.audit;

import com.lims.common.entity.BaseEntity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a flat, serialisation-safe snapshot of an entity for the audit trail: scalar columns
 * are copied, to-one associations are recorded by id (without initialising lazy proxies) and
 * collections are skipped. Serialising live entities directly fails on Hibernate proxies and can
 * trigger lazy loading in the middle of a flush.
 */
final class AuditSnapshot {

    private AuditSnapshot() {}

    static Map<String, Object> of(Object entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Class<?> type = entity.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                Object value = read(field, entity);
                if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
                    snapshot.putIfAbsent(field.getName() + "Id",
                            value instanceof BaseEntity related ? related.getId() : null);
                } else if (isScalar(field.getType())) {
                    snapshot.putIfAbsent(field.getName(), value);
                }
            }
        }
        return snapshot;
    }

    private static boolean isScalar(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || UUID.class == type
                || Temporal.class.isAssignableFrom(type);
    }

    private static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
