package com.lims.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        String accessionNumber,
        UUID patientId,
        String uhid,
        String patientFullName,
        String patientPhone,
        String patientGender,
        int patientAge,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        String referringDoctor,
        List<OrderTestItemDto> items,
        List<SampleBarcodeItemDto> samples,
        Instant occurredAt
) implements DomainEvent {

    public OrderCreatedEvent {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public record OrderTestItemDto(
            UUID testId,
            String testCode,
            String testName,
            BigDecimal price,
            String containerType
    ) {}

    public record SampleBarcodeItemDto(
            String barcode,
            String containerType,
            String specimenType
    ) {}
}
