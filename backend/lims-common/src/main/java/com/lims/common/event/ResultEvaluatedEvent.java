package com.lims.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResultEvaluatedEvent(
        UUID resultId,
        UUID orderId,
        UUID parameterId,
        String parameterName,
        String observedValue,
        boolean isCritical,
        boolean isOutOfRange,
        boolean requiresReview,
        BigDecimal deltaChangePercentage,
        Instant occurredAt
) implements DomainEvent {

    public ResultEvaluatedEvent {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
