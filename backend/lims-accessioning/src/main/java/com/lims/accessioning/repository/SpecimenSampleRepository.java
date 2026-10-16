package com.lims.accessioning.repository;

import com.lims.accessioning.entity.SpecimenSample;
import com.lims.common.enums.SampleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpecimenSampleRepository extends JpaRepository<SpecimenSample, UUID> {
    Optional<SpecimenSample> findByBarcode(String barcode);
    boolean existsByBarcode(String barcode);
    List<SpecimenSample> findByOrderId(UUID orderId);
    Page<SpecimenSample> findByStatus(SampleStatus status, Pageable pageable);
}
