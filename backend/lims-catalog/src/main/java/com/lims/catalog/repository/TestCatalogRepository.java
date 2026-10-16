package com.lims.catalog.repository;

import com.lims.catalog.entity.TestCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Only one collection is join-fetched per query: fetching both {@code parameters} and
 * {@code parameters.referenceRanges} (two bags) raises MultipleBagFetchException.
 * Reference ranges are loaded through {@code hibernate.default_batch_fetch_size} instead.
 */
@Repository
public interface TestCatalogRepository extends JpaRepository<TestCatalog, UUID> {

    Optional<TestCatalog> findByTestCode(String testCode);

    @Query("SELECT DISTINCT t FROM TestCatalog t LEFT JOIN FETCH t.parameters WHERE t.isActive = true ORDER BY t.testName ASC")
    List<TestCatalog> findActiveWithParameters();

    @Query("SELECT DISTINCT t FROM TestCatalog t LEFT JOIN FETCH t.parameters WHERE t.testCode = :testCode")
    Optional<TestCatalog> findByTestCodeWithDetails(String testCode);

    @Query("SELECT DISTINCT t FROM TestCatalog t LEFT JOIN FETCH t.parameters WHERE t.id = :id")
    Optional<TestCatalog> findByIdWithDetails(UUID id);
}
