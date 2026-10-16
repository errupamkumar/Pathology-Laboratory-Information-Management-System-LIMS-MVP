package com.lims.catalog.service;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.entity.ParameterReferenceRange;
import com.lims.catalog.entity.TestCatalog;
import com.lims.catalog.repository.ParameterReferenceRangeRepository;
import com.lims.catalog.repository.TestCatalogRepository;
import com.lims.common.enums.Gender;
import com.lims.common.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only catalog API. Returns immutable DTOs (never entities) so results are safe to cache
 * and other modules do not couple to catalog JPA mappings.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    public static final String CACHE_ACTIVE_TESTS = "catalog.activeTests";
    public static final String CACHE_TEST_BY_ID = "catalog.testById";
    public static final String CACHE_TEST_BY_CODE = "catalog.testByCode";
    public static final String CACHE_REFERENCE_RANGES = "catalog.referenceRanges";

    private final TestCatalogRepository testCatalogRepository;
    private final ParameterReferenceRangeRepository parameterReferenceRangeRepository;

    public CatalogService(TestCatalogRepository testCatalogRepository,
                          ParameterReferenceRangeRepository parameterReferenceRangeRepository) {
        this.testCatalogRepository = testCatalogRepository;
        this.parameterReferenceRangeRepository = parameterReferenceRangeRepository;
    }

    @Cacheable(CACHE_ACTIVE_TESTS)
    public List<TestCatalogDto> getAllActiveTests() {
        return testCatalogRepository.findActiveWithParameters()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Cacheable(CACHE_TEST_BY_CODE)
    public TestCatalogDto getTestByCode(String code) {
        return testCatalogRepository.findByTestCodeWithDetails(code)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("TestCatalog", code));
    }

    @Cacheable(CACHE_TEST_BY_ID)
    public TestCatalogDto getTestById(UUID id) {
        return testCatalogRepository.findByIdWithDetails(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("TestCatalog", id));
    }

    /**
     * Best-matching interval for the patient: gender-specific ranges win over {@code ALL}.
     */
    @Cacheable(value = CACHE_REFERENCE_RANGES, key = "#parameterId + ':' + #age + ':' + #gender")
    public Optional<TestCatalogDto.ReferenceRangeDto> getReferenceRangeForParameter(UUID parameterId, int age, Gender gender) {
        return parameterReferenceRangeRepository.findBestMatchingRange(parameterId, age, gender)
                .map(CatalogService::toRangeDto);
    }

    public TestCatalogDto mapToDto(TestCatalog entity) {
        List<TestCatalogDto.TestParameterDto> params = entity.getParameters().stream()
                .map(p -> new TestCatalogDto.TestParameterDto(
                        p.getId(),
                        p.getParameterName(),
                        p.getUnit(),
                        p.getFormula(),
                        p.getDisplayOrder(),
                        p.getReferenceRanges().stream().map(CatalogService::toRangeDto).toList()
                )).toList();

        return new TestCatalogDto(
                entity.getId(),
                entity.getTestCode(),
                entity.getTestName(),
                entity.getDepartment(),
                entity.getSpecimenType(),
                entity.getContainerType(),
                entity.getPrice(),
                entity.getTatMinutes(),
                entity.getIsActive(),
                params
        );
    }

    static TestCatalogDto.ReferenceRangeDto toRangeDto(ParameterReferenceRange r) {
        return new TestCatalogDto.ReferenceRangeDto(
                r.getId(),
                r.getGender().name(),
                r.getMinAge(),
                r.getMaxAge(),
                r.getNormalLow(),
                r.getNormalHigh(),
                r.getCriticalLow(),
                r.getCriticalHigh(),
                r.getTextNormalVal()
        );
    }
}
