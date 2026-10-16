package com.lims.patient.dto;

import com.lims.common.enums.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponseDto(
        UUID id,
        String uhid,
        String firstName,
        String lastName,
        String fullName,
        LocalDate dob,
        int age,
        Gender gender,
        String phone,
        String email,
        Instant createdAt
) {}
