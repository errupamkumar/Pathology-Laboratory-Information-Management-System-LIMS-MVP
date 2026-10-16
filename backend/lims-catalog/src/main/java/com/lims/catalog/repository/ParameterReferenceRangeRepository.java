package com.lims.catalog.repository;

import com.lims.catalog.entity.ParameterReferenceRange;
import com.lims.common.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParameterReferenceRangeRepository extends JpaRepository<ParameterReferenceRange, UUID> {

    @Query("""
        SELECT r FROM ParameterReferenceRange r
        WHERE r.parameter.id = :parameterId
          AND :age BETWEEN r.minAge AND r.maxAge
          AND (r.gender = :gender OR r.gender = com.lims.common.enums.Gender.ALL)
        ORDER BY CASE WHEN r.gender = :gender THEN 0 ELSE 1 END ASC
    """)
    List<ParameterReferenceRange> findMatchingRanges(UUID parameterId, int age, Gender gender);

    default Optional<ParameterReferenceRange> findBestMatchingRange(UUID parameterId, int age, Gender gender) {
        List<ParameterReferenceRange> ranges = findMatchingRanges(parameterId, age, gender);
        return ranges.isEmpty() ? Optional.empty() : Optional.of(ranges.getFirst());
    }
}
