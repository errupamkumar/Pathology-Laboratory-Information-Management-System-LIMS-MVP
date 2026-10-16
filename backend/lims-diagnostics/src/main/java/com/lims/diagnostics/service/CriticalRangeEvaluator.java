package com.lims.diagnostics.service;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.service.CatalogService;
import com.lims.common.enums.Gender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
public class CriticalRangeEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CriticalRangeEvaluator.class);

    private final CatalogService catalogService;

    public CriticalRangeEvaluator(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public EvaluationResult evaluate(UUID parameterId, String observedValue, int patientAge, Gender patientGender) {
        if (observedValue == null || observedValue.isBlank()) {
            return EvaluationResult.NONE;
        }

        Optional<TestCatalogDto.ReferenceRangeDto> rangeOpt = catalogService.getReferenceRangeForParameter(
                parameterId, patientAge, patientGender
        );

        if (rangeOpt.isEmpty()) {
            log.debug("No reference range configured for parameterId: {}, age: {}, gender: {}", parameterId, patientAge, patientGender);
            return EvaluationResult.NONE;
        }

        TestCatalogDto.ReferenceRangeDto range = rangeOpt.get();

        BigDecimal numVal;
        try {
            numVal = new BigDecimal(observedValue.trim());
        } catch (NumberFormatException e) {
            // Qualitative test comparison
            boolean isOutOfRange = range.textNormalVal() != null && !range.textNormalVal().isBlank()
                    && !observedValue.trim().equalsIgnoreCase(range.textNormalVal().trim());
            return new EvaluationResult(isOutOfRange, false, null, null, null, null, range.textNormalVal());
        }

        boolean isOutOfRange = (range.normalLow() != null && numVal.compareTo(range.normalLow()) < 0)
                || (range.normalHigh() != null && numVal.compareTo(range.normalHigh()) > 0);

        boolean isCritical = (range.criticalLow() != null && numVal.compareTo(range.criticalLow()) < 0)
                || (range.criticalHigh() != null && numVal.compareTo(range.criticalHigh()) > 0);

        return new EvaluationResult(
                isOutOfRange || isCritical,
                isCritical,
                range.normalLow(),
                range.normalHigh(),
                range.criticalLow(),
                range.criticalHigh(),
                range.textNormalVal()
        );
    }

    public record EvaluationResult(
            boolean isOutOfRange,
            boolean isCritical,
            BigDecimal normalLow,
            BigDecimal normalHigh,
            BigDecimal criticalLow,
            BigDecimal criticalHigh,
            String textNormalVal
    ) {
        static final EvaluationResult NONE = new EvaluationResult(false, false, null, null, null, null, null);
    }
}
