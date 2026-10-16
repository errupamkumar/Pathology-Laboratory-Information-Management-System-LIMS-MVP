package com.lims.patient.dto;

import com.lims.common.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
        UUID id,
        String accessionNumber,
        PatientResponseDto patient,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        String referringDoctor,
        Instant createdAt,
        List<OrderItemDto> items
) {
    public record OrderItemDto(
            UUID id,
            UUID testCatalogId,
            String testCode,
            String testName,
            BigDecimal price
    ) {}
}
