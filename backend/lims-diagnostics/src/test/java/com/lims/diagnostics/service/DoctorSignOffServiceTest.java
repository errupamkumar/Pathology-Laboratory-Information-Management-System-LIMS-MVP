package com.lims.diagnostics.service;

import com.lims.common.api.ClinicalOrderGateway;
import com.lims.common.api.OrderSnapshot;
import com.lims.common.enums.Gender;
import com.lims.common.enums.OrderStatus;
import com.lims.common.event.OrderAuthorizedEvent;
import com.lims.common.exception.InvalidOperationException;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lims.diagnostics.entity.OrderAuthorization;
import com.lims.diagnostics.entity.TestResult;
import com.lims.diagnostics.repository.OrderAuthorizationRepository;
import com.lims.diagnostics.repository.TestResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorSignOffServiceTest {

    @Mock ClinicalOrderGateway orderGateway;
    @Mock TestResultRepository resultRepository;
    @Mock OrderAuthorizationRepository authorizationRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks DoctorSignOffService service;

    final OrderSnapshot order = new OrderSnapshot(UUID.randomUUID(), "ACC-2026-5001", OrderStatus.COMPLETED, "Self",
            Instant.now(), UUID.randomUUID(), "UHID-2026-1001", "Sarah Jenkins", 44, Gender.FEMALE);

    @BeforeEach
    void signedInPathologist() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("dr.sharma", "n/a", "ROLE_PATHOLOGIST"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signsReportBindsAccountAndPublishesEvent() {
        when(orderGateway.getOrder(order.orderId())).thenReturn(order);
        when(authorizationRepository.findByOrderId(order.orderId())).thenReturn(Optional.empty());
        when(resultRepository.findByOrderId(order.orderId())).thenReturn(List.of(mock(TestResult.class)));
        when(authorizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultEntryDtos.AuthorizationDto auth = service.authorizeOrder(order.orderId(), " Dr. Rajesh Sharma ", "MCI-1");

        assertThat(auth.authorizedBy()).isEqualTo("dr.sharma");
        assertThat(auth.pathologistName()).isEqualTo("Dr. Rajesh Sharma");
        assertThat(auth.verificationToken()).matches("VERIFY-[0-9A-F]{16}");
        assertThat(auth.digitalSignatureHash()).hasSize(64);
        verify(orderGateway).updateStatus(order.orderId(), OrderStatus.AUTHORIZED);
        verify(eventPublisher).publishEvent(any(OrderAuthorizedEvent.class));
    }

    @Test
    void cannotSignWithoutResults() {
        when(orderGateway.getOrder(order.orderId())).thenReturn(order);
        when(authorizationRepository.findByOrderId(order.orderId())).thenReturn(Optional.empty());
        when(resultRepository.findByOrderId(order.orderId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.authorizeOrder(order.orderId(), "Dr", "L"))
                .isInstanceOf(InvalidOperationException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reAuthorizingReturnsExistingSignature() {
        OrderAuthorization existing = new OrderAuthorization(UUID.randomUUID(), order.orderId(), "Dr A", "L1",
                "hash", "VERIFY-0011223344556677", "dr.a", Instant.now());
        when(orderGateway.getOrder(order.orderId())).thenReturn(order);
        when(authorizationRepository.findByOrderId(order.orderId())).thenReturn(Optional.of(existing));

        assertThat(service.authorizeOrder(order.orderId(), "Dr B", "L2").verificationToken())
                .isEqualTo("VERIFY-0011223344556677");
        verify(authorizationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
