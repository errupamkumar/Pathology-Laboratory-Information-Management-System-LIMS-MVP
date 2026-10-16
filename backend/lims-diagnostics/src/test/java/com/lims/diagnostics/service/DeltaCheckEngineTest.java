package com.lims.diagnostics.service;

import com.lims.common.api.ClinicalOrderGateway;
import com.lims.diagnostics.entity.TestResult;
import com.lims.diagnostics.repository.TestResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeltaCheckEngineTest {

    final UUID patient = UUID.randomUUID();
    final UUID current = UUID.randomUUID();
    final UUID param = UUID.randomUUID();

    @Mock ClinicalOrderGateway orderGateway;
    @Mock TestResultRepository resultRepository;
    @InjectMocks DeltaCheckEngine engine;

    @Test
    void comparesWithMostRecentPriorOrderThatMeasuredTheAnalyte() {
        UUID newest = UUID.randomUUID();   // analyte not tested here
        UUID middle = UUID.randomUUID();   // most recent measurement: 10.0
        UUID oldest = UUID.randomUUID();   // older measurement: 20.0 (must be ignored)
        when(orderGateway.findPriorOrderIds(patient, current)).thenReturn(List.of(newest, middle, oldest));
        when(resultRepository.findByParameterIdAndOrderIdIn(param, List.of(newest, middle, oldest)))
                .thenReturn(List.of(result(oldest, "20.0"), result(middle, "10.0")));

        DeltaCheckEngine.DeltaCheckResult delta = engine.evaluateDelta(patient, current, param, "13.0");

        assertThat(delta.previousValue()).isEqualByComparingTo("10.0");
        assertThat(delta.deltaChangePercentage()).isEqualByComparingTo("30.00");
        assertThat(delta.requiresReview()).isTrue();
    }

    @Test
    void changeWithinThresholdDoesNotRequireReview() {
        UUID prior = UUID.randomUUID();
        when(orderGateway.findPriorOrderIds(patient, current)).thenReturn(List.of(prior));
        when(resultRepository.findByParameterIdAndOrderIdIn(param, List.of(prior))).thenReturn(List.of(result(prior, "100")));

        DeltaCheckEngine.DeltaCheckResult delta = engine.evaluateDelta(patient, current, param, "80");

        assertThat(delta.deltaChangePercentage()).isEqualByComparingTo("-20.00");
        assertThat(delta.requiresReview()).isFalse();
    }

    @Test
    void noPriorOrdersMeansNotApplicable() {
        when(orderGateway.findPriorOrderIds(patient, current)).thenReturn(List.of());

        DeltaCheckEngine.DeltaCheckResult delta = engine.evaluateDelta(patient, current, param, "13.0");

        assertThat(delta.requiresReview()).isFalse();
        assertThat(delta.deltaChangePercentage()).isNull();
    }

    @Test
    void qualitativeValuesSkipDeltaCheck() {
        DeltaCheckEngine.DeltaCheckResult delta = engine.evaluateDelta(patient, current, param, "Positive");

        assertThat(delta.deltaChangePercentage()).isNull();
        verifyNoInteractions(orderGateway, resultRepository);
    }

    @Test
    void zeroPreviousValueAvoidsDivisionByZero() {
        UUID prior = UUID.randomUUID();
        when(orderGateway.findPriorOrderIds(patient, current)).thenReturn(List.of(prior));
        when(resultRepository.findByParameterIdAndOrderIdIn(param, List.of(prior))).thenReturn(List.of(result(prior, "0")));

        DeltaCheckEngine.DeltaCheckResult delta = engine.evaluateDelta(patient, current, param, "5");

        assertThat(delta.requiresReview()).isFalse();
        assertThat(delta.deltaChangePercentage()).isNull();
    }

    private TestResult result(UUID orderId, String value) {
        return new TestResult(UUID.randomUUID(), orderId, param, "Hemoglobin", "g/dL", value);
    }
}
