package com.lims.common.event;

import com.lims.common.enums.SampleStatus;

import java.time.Instant;
import java.util.UUID;

public record SampleAccessionedEvent(
        UUID sampleId,
        UUID orderId,
        String accessionNumber,
        String barcode,
        SampleStatus status,
        String rejectionReason,
        String rejectionNotes,
        Instant occurredAt
) implements DomainEvent {

    public SampleAccessionedEvent {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
