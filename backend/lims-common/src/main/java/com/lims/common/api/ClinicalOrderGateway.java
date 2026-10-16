package com.lims.common.api;

import com.lims.common.enums.OrderStatus;
import com.lims.common.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Anti-corruption port through which downstream modules (diagnostics, printing) read and advance
 * diagnostic orders without compiling against lims-patient's entities or repositories.
 * Implemented by lims-patient; participates in the caller's transaction.
 */
public interface ClinicalOrderGateway {

    Optional<OrderSnapshot> findOrder(UUID orderId);

    default OrderSnapshot getOrder(UUID orderId) {
        return findOrder(orderId).orElseThrow(() -> new ResourceNotFoundException("DiagnosticOrder", orderId));
    }

    /**
     * @return IDs of the patient's orders created strictly before {@code beforeOrderId}, newest first
     */
    List<UUID> findPriorOrderIds(UUID patientId, UUID beforeOrderId);

    void updateStatus(UUID orderId, OrderStatus status);
}
