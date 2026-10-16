package com.lims.catalog.service;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.entity.ParameterReferenceRange;
import com.lims.catalog.entity.TestCatalog;
import com.lims.catalog.entity.TestParameter;
import com.lims.catalog.repository.ParameterReferenceRangeRepository;
import com.lims.catalog.repository.TestCatalogRepository;
import com.lims.common.enums.Gender;
import com.lims.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    TestCatalogRepository testCatalogRepository;
    @Mock
    ParameterReferenceRangeRepository rangeRepository;
    @InjectMocks
    CatalogService service;

    @Test
    void mapsCatalogWithNestedParametersAndRanges() {
        TestCatalog cbc = new TestCatalog(UUID.randomUUID(), "CBC", "Complete Blood Count", "HEMATOLOGY",
                "Whole Blood", "EDTA (Lavender)", new BigDecimal("450.00"), 120);
        TestParameter hb = new TestParameter(UUID.randomUUID(), "Hemoglobin (Hb)", "g/dL", null, 1);
        hb.addReferenceRange(range(Gender.FEMALE));
        cbc.addParameter(hb);
        when(testCatalogRepository.findActiveWithParameters()).thenReturn(List.of(cbc));

        List<TestCatalogDto> tests = service.getAllActiveTests();

        assertThat(tests).singleElement().satisfies(dto -> {
            assertThat(dto.testCode()).isEqualTo("CBC");
            assertThat(dto.parameters()).singleElement().satisfies(p -> {
                assertThat(p.parameterName()).isEqualTo("Hemoglobin (Hb)");
                assertThat(p.referenceRanges()).singleElement()
                        .satisfies(r -> assertThat(r.gender()).isEqualTo("FEMALE"));
            });
        });
    }

    @Test
    void referenceRangeIsReturnedAsDtoNotEntity() {
        UUID parameterId = UUID.randomUUID();
        when(rangeRepository.findBestMatchingRange(parameterId, 40, Gender.FEMALE)).thenReturn(Optional.of(range(Gender.FEMALE)));

        Optional<TestCatalogDto.ReferenceRangeDto> range = service.getReferenceRangeForParameter(parameterId, 40, Gender.FEMALE);

        assertThat(range).hasValueSatisfying(r -> {
            assertThat(r.normalLow()).isEqualByComparingTo("12.0");
            assertThat(r.criticalHigh()).isEqualByComparingTo("20.0");
        });
    }

    @Test
    void unknownTestIsNotFound() {
        UUID id = UUID.randomUUID();
        when(testCatalogRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTestById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static ParameterReferenceRange range(Gender gender) {
        return new ParameterReferenceRange(UUID.randomUUID(), gender, 12, 120,
                new BigDecimal("12.0"), new BigDecimal("15.5"), new BigDecimal("7.0"), new BigDecimal("20.0"), null);
    }
}
