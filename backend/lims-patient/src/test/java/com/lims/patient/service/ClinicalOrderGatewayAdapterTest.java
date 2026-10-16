package com.lims.patient.service;

import com.lims.common.api.OrderSnapshot;
import com.lims.common.enums.Gender;
import com.lims.common.enums.OrderStatus;
import com.lims.common.exception.InvalidOperationException;
import com.lims.patient.entity.DiagnosticOrder;
import com.lims.patient.entity.Patient;
import com.lims.patient.repository.DiagnosticOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalOrderGatewayAdapterTest {

    @Mock DiagnosticOrderRepository repository;
    @InjectMocks ClinicalOrderGatewayAdapter gateway;

    @Test
    void snapshotCarriesDemographicsWithoutEntities() {
        DiagnosticOrder order = order(OrderStatus.REGISTERED);
        when(repository.findByIdWithPatient(order.getId())).thenReturn(Optional.of(order));

        OrderSnapshot snapshot = gateway.findOrder(order.getId()).orElseThrow();

        assertThat(snapshot.accessionNumber()).isEqualTo("ACC-2026-5001");
        assertThat(snapshot.patientGender()).isEqualTo(Gender.FEMALE);
        assertThat(snapshot.patientUhid()).isEqualTo("UHID-2026-1001");
    }

    @Test
    void authorizedOrderStatusIsFinal() {
        DiagnosticOrder order = order(OrderStatus.AUTHORIZED);
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> gateway.updateStatus(order.getId(), OrderStatus.COMPLETED))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void advancesStatus() {
        DiagnosticOrder order = order(OrderStatus.REGISTERED);
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        gateway.updateStatus(order.getId(), OrderStatus.COMPLETED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void priorOrdersAreThoseCreatedBeforeTheReferenceOrder() {
        DiagnosticOrder current = order(OrderStatus.REGISTERED);
        UUID older = UUID.randomUUID();
        when(repository.findById(current.getId())).thenReturn(Optional.of(current));
        when(repository.findIdsForPatientCreatedBefore(current.getPatient().getId(), current.getCreatedAt())).thenReturn(List.of(older));

        assertThat(gateway.findPriorOrderIds(current.getPatient().getId(), current.getId())).containsExactly(older);
    }

    private static DiagnosticOrder order(OrderStatus status) {
        Patient patient = new Patient(UUID.randomUUID(), "UHID-2026-1001", "Sarah", "Jenkins",
                LocalDate.of(1982, 5, 14), Gender.FEMALE, "+91 98112 34567", null);
        DiagnosticOrder order = new DiagnosticOrder(UUID.randomUUID(), "ACC-2026-5001", patient,
                new BigDecimal("570"), new BigDecimal("570"), "Self");
        order.setStatus(status);
        return order;
    }
}
