package com.lims.printing.controller;

import com.lims.common.security.Roles;
import com.lims.common.web.ApiPaths;
import com.lims.printing.model.PrintJob;
import com.lims.printing.service.ZeroClickSpoolerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/printing")
@PreAuthorize(Roles.CAN_VIEW_CLINICAL)
@Tag(name = "Zero-Click Silent Printing & Spooler", description = "Hardware spooler status, PDF report generation, and re-spool endpoints")
public class PrintingController {

    private final ZeroClickSpoolerService spoolerService;

    public PrintingController(ZeroClickSpoolerService spoolerService) {
        this.spoolerService = spoolerService;
    }

    @GetMapping(value = "/pdf/{orderId}", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download or stream clinical A4 diagnostic PDF report")
    public ResponseEntity<byte[]> getPdfReport(@PathVariable UUID orderId) {
        byte[] pdfBytes = spoolerService.getReportPdfBytes(orderId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"lab-report-" + orderId + ".pdf\"")
                // PHI: never let browsers or proxies persist the report
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @PostMapping("/spool/report/{orderId}")
    @Operation(summary = "Manually re-spool clinical PDF report to hardware print agents")
    public ResponseEntity<PrintJob> spoolReport(@PathVariable UUID orderId) {
        return ResponseEntity.ok(spoolerService.spoolLaserReport(orderId));
    }

    @GetMapping("/jobs")
    @Operation(summary = "Get recent silent print jobs spooled through WebSocket")
    public ResponseEntity<List<PrintJob>> getRecentJobs() {
        return ResponseEntity.ok(spoolerService.getRecentJobs());
    }

    @GetMapping("/status")
    @Operation(summary = "Get print agent spooler connection status")
    public ResponseEntity<Map<String, Object>> getSpoolerStatus() {
        return ResponseEntity.ok(Map.of(
                "activeBridgeAgents", spoolerService.getActiveAgentCount(),
                "spooledJobsCount", spoolerService.getRecentJobs().size(),
                "status", "ONLINE",
                "endpoint", "/ws/print-agent"
        ));
    }
}
