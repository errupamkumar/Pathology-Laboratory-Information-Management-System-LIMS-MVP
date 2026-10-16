package com.lims.diagnostics.service;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.service.CatalogService;
import com.lims.common.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriticalRangeEvaluatorTest {

    static final UUID HB = UUID.randomUUID();

    @Mock CatalogService catalogService;
    @InjectMocks CriticalRangeEvaluator evaluator;

    @BeforeEach
    void femaleHemoglobinRange() {
        // Normal 12.0-15.5 g/dL, panic <7.0 or >20.0
        lenient().when(catalogService.getReferenceRangeForParameter(HB, 40, Gender.FEMALE)).thenReturn(Optional.of(
                new TestCatalogDto.ReferenceRangeDto(UUID.randomUUID(), "FEMALE", 12, 120,
                        new BigDecimal("12.0"), new BigDecimal("15.5"), new BigDecimal("7.0"), new BigDecimal("20.0"), null)));
    }

    @ParameterizedTest(name = "{0} g/dL -> outOfRange={1}, critical={2}")
    @CsvSource({
            "13.4, false, false",
            "12.0, false, false",   // boundary values are within range
            "15.5, false, false",
            "11.9, true,  false",
            "16.0, true,  false",
            "6.9,  true,  true",
            "21.0, true,  true",
            "' 5.1 ', true, true"   // whitespace tolerated
    })
    void classifiesNumericResults(String value, boolean outOfRange, boolean critical) {
        CriticalRangeEvaluator.EvaluationResult result = evaluator.evaluate(HB, value, 40, Gender.FEMALE);

        assertThat(result.isOutOfRange()).isEqualTo(outOfRange);
        assertThat(result.isCritical()).isEqualTo(critical);
        assertThat(result.normalLow()).isEqualByComparingTo("12.0");
    }

    @Test
    void qualitativeResultComparedWithExpectedText() {
        UUID urineGlucose = UUID.randomUUID();
        when(catalogService.getReferenceRangeForParameter(urineGlucose, 40, Gender.MALE)).thenReturn(Optional.of(
                new TestCatalogDto.ReferenceRangeDto(UUID.randomUUID(), "ALL", 0, 120, null, null, null, null, "Negative")));

        assertThat(evaluator.evaluate(urineGlucose, "negative", 40, Gender.MALE).isOutOfRange()).isFalse();
        assertThat(evaluator.evaluate(urineGlucose, "Positive (++)", 40, Gender.MALE).isOutOfRange()).isTrue();
    }

    @Test
    void noConfiguredRangeMeansNoFlags() {
        UUID unknown = UUID.randomUUID();
        when(catalogService.getReferenceRangeForParameter(unknown, 40, Gender.FEMALE)).thenReturn(Optional.empty());

        CriticalRangeEvaluator.EvaluationResult result = evaluator.evaluate(unknown, "999", 40, Gender.FEMALE);

        assertThat(result.isOutOfRange()).isFalse();
        assertThat(result.isCritical()).isFalse();
    }
}
