package com.lims.diagnostics.service;

import com.lims.catalog.service.CatalogService;
import com.lims.common.api.ClinicalOrderGateway;
import com.lims.common.api.OrderSnapshot;
import com.lims.common.enums.Gender;
import com.lims.common.enums.OrderStatus;
import com.lims.common.event.ResultEvaluatedEvent;
import com.lims.common.exception.InvalidOperationException;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lims.diagnostics.repository.OrderAuthorizationRepository;
import com.lims.diagnostics.repository.TestResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultEntryServiceTest {

    @Mock ClinicalOrderGateway orderGateway;
    @Mock TestResultRepository resultRepository;
    @Mock OrderAuthorizationRepository authorizationRepository;
    @Mock CriticalRangeEvaluator evaluator;
    @Mock DeltaCheckEngine deltaCheckEngine;
    @Mock CatalogService catalogService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ResultEntryService service;

    final UUID param = UUID.randomUUID();

    @Test
    void savesEvaluatedResultAndCompletesOrder() {
        OrderSnapshot order = order(OrderStatus.REGISTERED);
        when(orderGateway.getOrder(order.orderId())).thenReturn(order);
        when(authorizationRepository.findByOrderId(order.orderId())).thenReturn(Optional.empty());
        when(resultRepository.findByOrderIdAndParameterId(order.orderId(), param)).thenReturn(Optional.empty());
        when(resultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(evaluator.evaluate(eq(param), eq("5.1"), anyInt(), eq(Gender.FEMALE))).thenReturn(
                new CriticalRangeEvaluator.EvaluationResult(true, true, new BigDecimal("12"), new BigDecimal("15.5"),
                        new BigDecimal("7"), new BigDecimal("20"), null));
        when(deltaCheckEngine.evaluateDelta(order.patientId(), order.orderId(), param, "5.1"))
                .thenReturn(new DeltaCheckEngine.DeltaCheckResult(false, null, null));

        List<ResultEntryDtos.TestResultDto> saved = service.saveResults(request(order.orderId(), " 5.1 "));

        assertThat(saved).singleElement().satisfies(r -> {
            assertThat(r.observedValue()).isEqualTo("5.1");
            assertThat(r.isCritical()).isTrue();
            assertThat(r.criticalLow()).isEqualByComparingTo("7");
        });
        verify(orderGateway).updateStatus(order.orderId(), OrderStatus.COMPLETED);
        verify(eventPublisher).publishEvent(any(ResultEvaluatedEvent.class));
    }

    @Test
    void authorizedResultsAreLocked() {
        OrderSnapshot order = order(OrderStatus.AUTHORIZED);
        when(orderGateway.getOrder(order.orderId())).thenReturn(order);

        assertThatThrownBy(() -> service.saveResults(request(order.orderId(), "13.0")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("locked");
        verify(resultRepository, never()).save(any());
    }

    private ResultEntryDtos.BatchResultEntryRequest request(UUID orderId, String value) {
        return new ResultEntryDtos.BatchResultEntryRequest(orderId,
                List.of(new ResultEntryDtos.ParameterResultInput(param, "Hemoglobin (Hb)", "g/dL", value, null)));
    }

    private static OrderSnapshot order(OrderStatus status) {
        return new OrderSnapshot(UUID.randomUUID(), "ACC-2026-5001", status, "Self", Instant.now(),
                UUID.randomUUID(), "UHID-2026-1001", "Sarah Jenkins", 44, Gender.FEMALE);
    }
}
