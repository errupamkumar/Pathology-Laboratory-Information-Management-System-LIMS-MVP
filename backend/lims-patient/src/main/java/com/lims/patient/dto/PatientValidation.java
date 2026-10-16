package com.lims.patient.dto;

/**
 * Shared validation rules for patient-facing request DTOs (mirrored by the frontend zod schema).
 */
public final class PatientValidation {

    public static final String PHONE_REGEX = "^[0-9+()\\-\\s]{7,20}$";

    private PatientValidation() {}
}
