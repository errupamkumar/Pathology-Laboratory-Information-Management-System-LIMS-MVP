package com.lims.accessioning.controller;

import com.lims.accessioning.dto.AccessioningDtos;
import com.lims.accessioning.service.AccessioningService;
import com.lims.common.enums.SampleStatus;
import com.lims.common.security.Roles;
import com.lims.common.web.ApiPaths;
import com.lims.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/accessioning")
@Tag(name = "Sample Accessioning", description = "Sample receipt, barcode scanning, rejection workflows and chain of custody")
public class AccessioningController {

    private final AccessioningService accessioningService;

    public AccessioningController(AccessioningService accessioningService) {
        this.accessioningService = accessioningService;
    }

    @PostMapping("/scan-receive")
    @PreAuthorize(Roles.CAN_ACCESSION)
    @Operation(summary = "Barcode scan receipt", description = "Receives specimen sample into the lab chain of custody (idempotent for repeat scans)")
    public ResponseEntity<AccessioningDtos.SpecimenSampleDto> receiveSample(
            @Valid @RequestBody AccessioningDtos.ScanReceiveRequest request) {
        return ResponseEntity.ok(accessioningService.receiveSample(request.barcode()));
    }

    @PostMapping("/reject")
    @PreAuthorize(Roles.CAN_ACCESSION)
    @Operation(summary = "Reject specimen sample", description = "Rejects clotted, hemolyzed, or insufficient volume samples with audit trail")
    public ResponseEntity<AccessioningDtos.SpecimenSampleDto> rejectSample(
            @Valid @RequestBody AccessioningDtos.SampleRejectionRequest request) {
        return ResponseEntity.ok(accessioningService.rejectSample(request.barcode(), request.rejectionReason(), request.rejectionNotes()));
    }

    @GetMapping("/samples")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Get samples worklist (paged)", description = "Filter samples by status (COLLECTED, RECEIVED, REJECTED)")
    public ResponseEntity<PageResponse<AccessioningDtos.SpecimenSampleDto>> getSamples(
            @RequestParam(required = false) SampleStatus status,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(accessioningService.getSamples(status, pageable)));
    }

    @GetMapping("/samples/barcode/{barcode}")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Find sample by barcode")
    public ResponseEntity<AccessioningDtos.SpecimenSampleDto> getSampleByBarcode(
            @PathVariable @Pattern(regexp = AccessioningDtos.BARCODE_REGEX, message = "Barcode format is invalid") String barcode) {
        return ResponseEntity.ok(accessioningService.getSampleByBarcode(barcode));
    }

    @GetMapping("/samples/order/{orderId}")
    @PreAuthorize(Roles.CAN_VIEW_CLINICAL)
    @Operation(summary = "Get all samples for a diagnostic order")
    public ResponseEntity<List<AccessioningDtos.SpecimenSampleDto>> getSamplesByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(accessioningService.getSamplesByOrderId(orderId));
    }
}
