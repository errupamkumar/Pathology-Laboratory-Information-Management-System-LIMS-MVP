package com.lims.diagnostics.controller;

import com.lims.common.security.Roles;
import com.lims.common.web.ApiPaths;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lims.diagnostics.service.DoctorSignOffService;
import com.lims.diagnostics.service.ResultEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/diagnostics")
@Tag(name = "Diagnostics & Pathologist Sign-Off", description = "Result entry grid, critical panic range evaluation, delta checks, and doctor digital authorization")
public class DiagnosticsController {

    private final ResultEntryService resultEntryService;
    private final DoctorSignOffService doctorSignOffService;

    public DiagnosticsController(ResultEntryService resultEntryService,
                                 DoctorSignOffService doctorSignOffService) {
        this.resultEntryService = resultEntryService;
        this.doctorSignOffService = doctorSignOffService;
    }

    @PostMapping("/results/batch")
    @PreAuthorize(Roles.CAN_ENTER_RESULTS)
    @Operation(summary = "Batch Result Entry", description = "Saves observed values, executes delta checks against previous orders, and calculates panic thresholds. Rejected once the order is authorized.")
    public ResponseEntity<List<ResultEntryDtos.TestResultDto>> saveResults(
            @Valid @RequestBody ResultEntryDtos.BatchResultEntryRequest request) {
        return ResponseEntity.ok(resultEntryService.saveResults(request));
    }

    @GetMapping("/results/order/{orderId}")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Get order results and evaluation details")
    public ResponseEntity<ResultEntryDtos.OrderWithResultsDto> getOrderResults(@PathVariable UUID orderId) {
        return ResponseEntity.ok(resultEntryService.getOrderResultsWithDemographics(orderId));
    }

    @PostMapping("/authorize")
    @PreAuthorize(Roles.CAN_AUTHORIZE)
    @Operation(summary = "Pathologist Doctor Sign-Off", description = "PATHOLOGIST role only. Stamps a signature hash bound to the signed-in account, generates a verification token, marks the order AUTHORIZED and triggers the silent print spooler")
    public ResponseEntity<ResultEntryDtos.AuthorizationDto> authorizeOrder(
            @Valid @RequestBody ResultEntryDtos.AuthorizeOrderRequest request) {
        return ResponseEntity.ok(doctorSignOffService.authorizeOrder(
                request.orderId(),
                request.pathologistName(),
                request.licenseNumber()
        ));
    }

    @GetMapping("/verify/{verificationToken}")
    @SecurityRequirements // public endpoint: no bearer token required
    @Operation(summary = "Public report authenticity verification endpoint", description = "Unauthenticated. Returns signature details only - no patient data.")
    public ResponseEntity<ResultEntryDtos.ReportVerificationDto> verifyReport(
            @PathVariable @Pattern(regexp = "^VERIFY-[A-Za-z0-9]{4,32}$", message = "Invalid verification token") String verificationToken) {
        return ResponseEntity.ok(doctorSignOffService.verify(verificationToken));
    }
}
