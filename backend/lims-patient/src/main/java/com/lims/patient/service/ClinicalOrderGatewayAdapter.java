package com.lims.patient.service;

import com.lims.common.api.ClinicalOrderGateway;
import com.lims.common.api.OrderSnapshot;
import com.lims.common.enums.OrderStatus;
import com.lims.common.exception.InvalidOperationException;
import com.lims.common.exception.ResourceNotFoundException;
import com.lims.patient.entity.DiagnosticOrder;
import com.lims.patient.entity.Patient;
import com.lims.patient.repository.DiagnosticOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * lims-patient's implementation of the {@link ClinicalOrderGateway} port. Translates the order
 * aggregate into an {@link OrderSnapshot} so no JPA entity crosses the module boundary.
 */
@Component
@Transactional
public class ClinicalOrderGatewayAdapter implements ClinicalOrderGateway {

    private static final Logger log = LoggerFactory.getLogger(ClinicalOrderGatewayAdapter.class);

    private final DiagnosticOrderRepository diagnosticOrderRepository;

    public ClinicalOrderGatewayAdapter(DiagnosticOrderRepository diagnosticOrderRepository) {
        this.diagnosticOrderRepository = diagnosticOrderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> findOrder(UUID orderId) {
        return diagnosticOrderRepository.findByIdWithPatient(orderId).map(ClinicalOrderGatewayAdapter::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findPriorOrderIds(UUID patientId, UUID beforeOrderId) {
        DiagnosticOrder reference = diagnosticOrderRepository.findById(beforeOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticOrder", beforeOrderId));
        return diagnosticOrderRepository.findIdsForPatientCreatedBefore(patientId, reference.getCreatedAt());
    }

    @Override
    public void updateStatus(UUID orderId, OrderStatus status) {
        DiagnosticOrder order = diagnosticOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticOrder", orderId));
        if (order.getStatus() == status) {
            return;
        }
        if (order.getStatus() == OrderStatus.AUTHORIZED) {
            throw new InvalidOperationException("Order " + order.getAccessionNumber()
                    + " is already AUTHORIZED; its status can no longer change");
        }
        log.info("Order {} status {} -> {}", order.getAccessionNumber(), order.getStatus(), status);
        order.setStatus(status);
    }

    static OrderSnapshot toSnapshot(DiagnosticOrder order) {
        Patient patient = order.getPatient();
        return new OrderSnapshot(
                order.getId(),
                order.getAccessionNumber(),
                order.getStatus(),
                order.getReferringDoctor(),
                order.getCreatedAt(),
                patient.getId(),
                patient.getUhid(),
                patient.getFullName(),
                patient.getAge(),
                patient.getGender()
        );
    }
}
