package com.lims.catalog.repository;

import com.lims.catalog.entity.TestParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestParameterRepository extends JpaRepository<TestParameter, UUID> {
    List<TestParameter> findByTestCatalogIdOrderByDisplayOrderAsc(UUID testCatalogId);
}
