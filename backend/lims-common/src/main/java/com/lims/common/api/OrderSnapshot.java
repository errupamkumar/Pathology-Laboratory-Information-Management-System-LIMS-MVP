package com.lims.common.api;

import com.lims.common.enums.Gender;
import com.lims.common.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a diagnostic order and its patient demographics, shared across module
 * boundaries in place of the lims-patient JPA entities.
 */
public record OrderSnapshot(
        UUID orderId,
        String accessionNumber,
        OrderStatus status,
        String referringDoctor,
        Instant createdAt,
        UUID patientId,
        String patientUhid,
        String patientFullName,
        int patientAge,
        Gender patientGender
) {}
