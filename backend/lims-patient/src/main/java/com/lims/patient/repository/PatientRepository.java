package com.lims.patient.repository;

import com.lims.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByUhid(String uhid);
    Optional<Patient> findByPhone(String phone);
    boolean existsByPhone(String phone);
    boolean existsByUhid(String uhid);
    Page<Patient> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrPhoneContaining(
            String firstName, String lastName, String phone, Pageable pageable
    );
}
