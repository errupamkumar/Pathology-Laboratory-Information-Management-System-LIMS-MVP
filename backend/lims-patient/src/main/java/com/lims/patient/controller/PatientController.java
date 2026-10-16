package com.lims.patient.controller;

import com.lims.common.security.Roles;
import com.lims.common.web.ApiPaths;
import com.lims.common.web.PageResponse;
import com.lims.patient.dto.CreatePatientRequest;
import com.lims.patient.dto.PatientResponseDto;
import com.lims.patient.dto.PatientValidation;
import com.lims.patient.service.PatientRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/patients")
@Tag(name = "Patient Registration", description = "Patient demographics, duplicate phone verification and search")
public class PatientController {

    private final PatientRegistrationService patientRegistrationService;

    public PatientController(PatientRegistrationService patientRegistrationService) {
        this.patientRegistrationService = patientRegistrationService;
    }

    @PostMapping
    @PreAuthorize(Roles.CAN_REGISTER)
    @Operation(summary = "Register new patient", description = "Checks duplicate phone number and allocates unique UHID")
    public ResponseEntity<PatientResponseDto> registerPatient(@Valid @RequestBody CreatePatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientRegistrationService.registerPatient(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Get patient by ID")
    public ResponseEntity<PatientResponseDto> getPatientById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientRegistrationService.getPatientById(id));
    }

    @GetMapping("/phone/{phone}")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Lookup patient by phone number for quick duplicate check")
    public ResponseEntity<PatientResponseDto> findByPhone(
            @PathVariable @Pattern(regexp = PatientValidation.PHONE_REGEX, message = "Phone number format is invalid") String phone) {
        return patientRegistrationService.findPatientByPhone(phone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/search")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Search patients by name or phone (paged)")
    public ResponseEntity<PageResponse<PatientResponseDto>> searchPatients(
            @RequestParam(required = false) @Size(max = 100, message = "Search query is too long") String query,
            @ParameterObject @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(patientRegistrationService.searchPatients(query, pageable)));
    }
}
