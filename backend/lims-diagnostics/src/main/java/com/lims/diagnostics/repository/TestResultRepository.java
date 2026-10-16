package com.lims.diagnostics.repository;

import com.lims.diagnostics.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, UUID> {
    List<TestResult> findByOrderId(UUID orderId);
    Optional<TestResult> findByOrderIdAndParameterId(UUID orderId, UUID parameterId);
    List<TestResult> findByParameterIdAndOrderIdIn(UUID parameterId, List<UUID> orderIds);
}
