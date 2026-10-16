package com.lims.patient.controller;

import com.lims.common.security.Roles;
import com.lims.common.web.ApiPaths;
import com.lims.common.web.PageResponse;
import com.lims.patient.dto.OrderResponseDto;
import com.lims.patient.dto.RegisterPatientAndBookOrderRequest;
import com.lims.patient.service.PatientRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping(ApiPaths.V1 + "/orders")
@Tag(name = "Order Booking & Billing", description = "Diagnostic order registration, multi-test bill calculation and receipt enqueuing")
public class OrderController {

    private final PatientRegistrationService patientRegistrationService;

    public OrderController(PatientRegistrationService patientRegistrationService) {
        this.patientRegistrationService = patientRegistrationService;
    }

    @PostMapping("/book")
    @PreAuthorize(Roles.CAN_REGISTER)
    @Operation(summary = "Unified Quick Register & Order Booking", description = "Registers or matches patient, calculates multi-test billing, records payment, and fires OrderCreatedEvent")
    public ResponseEntity<OrderResponseDto> registerAndBookOrder(
            @Valid @RequestBody RegisterPatientAndBookOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientRegistrationService.registerAndBookOrder(request));
    }

    @GetMapping
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "List diagnostic orders (paged, newest first by default)")
    public ResponseEntity<PageResponse<OrderResponseDto>> getOrders(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(patientRegistrationService.getOrders(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Get diagnostic order by order ID")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientRegistrationService.getOrderById(id));
    }

    @GetMapping("/accession/{accessionNumber}")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Get diagnostic order by Accession Number (e.g. ACC-2026-5001)")
    public ResponseEntity<OrderResponseDto> getOrderByAccession(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9\\-]{1,64}$", message = "Invalid accession number") String accessionNumber) {
        return ResponseEntity.ok(patientRegistrationService.getOrderByAccession(accessionNumber));
    }
}
