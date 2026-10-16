package com.lims.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderAuthorizedEvent(
        UUID orderId,
        String accessionNumber,
        UUID patientId,
        String pathologistName,
        String licenseNumber,
        String verificationToken,
        Instant occurredAt
) implements DomainEvent {

    public OrderAuthorizedEvent {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
