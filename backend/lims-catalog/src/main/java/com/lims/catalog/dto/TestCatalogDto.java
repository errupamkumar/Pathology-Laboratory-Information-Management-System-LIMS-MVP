package com.lims.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TestCatalogDto(
        UUID id,
        String testCode,
        String testName,
        String department,
        String specimenType,
        String containerType,
        BigDecimal price,
        Integer tatMinutes,
        Boolean isActive,
        List<TestParameterDto> parameters
) {
    public record TestParameterDto(
            UUID id,
            String parameterName,
            String unit,
            String formula,
            Integer displayOrder,
            List<ReferenceRangeDto> referenceRanges
    ) {}

    public record ReferenceRangeDto(
            UUID id,
            String gender,
            Integer minAge,
            Integer maxAge,
            BigDecimal normalLow,
            BigDecimal normalHigh,
            BigDecimal criticalLow,
            BigDecimal criticalHigh,
            String textNormalVal
    ) {}
}
