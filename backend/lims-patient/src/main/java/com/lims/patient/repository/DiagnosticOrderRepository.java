package com.lims.patient.repository;

import com.lims.patient.entity.DiagnosticOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiagnosticOrderRepository extends JpaRepository<DiagnosticOrder, UUID> {

    @Query("SELECT o FROM DiagnosticOrder o JOIN FETCH o.patient LEFT JOIN FETCH o.items WHERE o.accessionNumber = :accessionNumber")
    Optional<DiagnosticOrder> findByAccessionNumberWithDetails(String accessionNumber);

    @Query("SELECT o FROM DiagnosticOrder o JOIN FETCH o.patient LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<DiagnosticOrder> findByIdWithDetails(UUID id);

    @Query("SELECT o FROM DiagnosticOrder o JOIN FETCH o.patient WHERE o.id = :id")
    Optional<DiagnosticOrder> findByIdWithPatient(UUID id);

    /**
     * Patient is a to-one join so it is safe to fetch alongside pagination; items are loaded in
     * batches via {@code hibernate.default_batch_fetch_size}.
     */
    @Query(value = "SELECT o FROM DiagnosticOrder o JOIN FETCH o.patient",
            countQuery = "SELECT COUNT(o) FROM DiagnosticOrder o")
    Page<DiagnosticOrder> findAllWithPatient(Pageable pageable);

    @Query("""
        SELECT o.id FROM DiagnosticOrder o
        WHERE o.patient.id = :patientId AND o.createdAt < :before
        ORDER BY o.createdAt DESC
    """)
    List<UUID> findIdsForPatientCreatedBefore(UUID patientId, Instant before);

    boolean existsByAccessionNumber(String accessionNumber);
}
