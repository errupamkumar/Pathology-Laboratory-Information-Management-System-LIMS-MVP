package com.lims.diagnostics.service;

import com.lims.common.api.ClinicalOrderGateway;
import com.lims.diagnostics.entity.TestResult;
import com.lims.diagnostics.repository.TestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares a numeric result with the patient's most recent <em>prior</em> result for the same
 * analyte (orders created before the current one, skipping orders where it was not tested).
 */
@Component
public class DeltaCheckEngine {

    private static final Logger log = LoggerFactory.getLogger(DeltaCheckEngine.class);
    static final BigDecimal DELTA_THRESHOLD_PERCENT = new BigDecimal("25.00");

    private final ClinicalOrderGateway orderGateway;
    private final TestResultRepository testResultRepository;

    public DeltaCheckEngine(ClinicalOrderGateway orderGateway, TestResultRepository testResultRepository) {
        this.orderGateway = orderGateway;
        this.testResultRepository = testResultRepository;
    }

    public DeltaCheckResult evaluateDelta(UUID patientId, UUID currentOrderId, UUID parameterId, String observedValue) {
        if (observedValue == null || observedValue.isBlank()) {
            return DeltaCheckResult.NOT_APPLICABLE;
        }

        BigDecimal currentValue;
        try {
            currentValue = new BigDecimal(observedValue.trim());
        } catch (NumberFormatException e) {
            // Qualitative parameter: delta check not applicable
            return DeltaCheckResult.NOT_APPLICABLE;
        }

        List<UUID> priorOrderIds = orderGateway.findPriorOrderIds(patientId, currentOrderId);
        if (priorOrderIds.isEmpty()) {
            log.debug("No prior orders found for patient {} to calculate delta", patientId);
            return DeltaCheckResult.NOT_APPLICABLE;
        }

        Map<UUID, TestResult> priorResultsByOrder = testResultRepository
                .findByParameterIdAndOrderIdIn(parameterId, priorOrderIds).stream()
                .collect(Collectors.toMap(TestResult::getOrderId, Function.identity(), (a, b) -> a));

        // priorOrderIds is newest-first, so the first hit is the most recent previous value
        Optional<TestResult> previous = priorOrderIds.stream()
                .map(priorResultsByOrder::get)
                .filter(r -> r != null)
                .findFirst();

        if (previous.isEmpty()) {
            log.debug("Parameter {} was not tested in any prior order of patient {}", parameterId, patientId);
            return DeltaCheckResult.NOT_APPLICABLE;
        }

        BigDecimal previousValue;
        try {
            previousValue = new BigDecimal(previous.get().getObservedValue().trim());
        } catch (NumberFormatException e) {
            return DeltaCheckResult.NOT_APPLICABLE;
        }
        if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
            return new DeltaCheckResult(false, null, previousValue);
        }

        // Delta % = ((current - previous) / previous) * 100
        BigDecimal percentage = currentValue.subtract(previousValue)
                .divide(previousValue, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        boolean requiresReview = percentage.abs().compareTo(DELTA_THRESHOLD_PERCENT) > 0;
        if (requiresReview) {
            log.warn("Delta check flag triggered! Parameter {} changed by {}% (previous: {}, current: {})",
                    parameterId, percentage, previousValue, currentValue);
        }
        return new DeltaCheckResult(requiresReview, percentage, previousValue);
    }

    public record DeltaCheckResult(
            boolean requiresReview,
            BigDecimal deltaChangePercentage,
            BigDecimal previousValue
    ) {
        static final DeltaCheckResult NOT_APPLICABLE = new DeltaCheckResult(false, null, null);
    }
}
