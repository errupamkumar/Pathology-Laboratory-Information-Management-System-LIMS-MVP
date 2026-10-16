package com.lims.printing.service;

import com.lims.common.event.OrderAuthorizedEvent;
import com.lims.common.event.OrderCreatedEvent;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lims.diagnostics.service.ResultEntryService;
import com.lims.printing.formatter.EscPosInvoiceFormatter;
import com.lims.printing.formatter.ZplBarcodeFormatter;
import com.lims.printing.model.PrintJob;
import com.lims.printing.pdf.ClinicalPdfReportGenerator;
import com.lims.printing.websocket.PrintAgentWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Print spooling is a side effect of business transactions, never part of them: listeners run
 * asynchronously after commit, so a printer/PDF failure cannot roll back an order or a sign-off.
 * Events are held in the publication registry until a listener succeeds.
 */
@Service
public class ZeroClickSpoolerService {

    private static final Logger log = LoggerFactory.getLogger(ZeroClickSpoolerService.class);

    private final PrintAgentWebSocketHandler webSocketHandler;
    private final ZplBarcodeFormatter zplBarcodeFormatter;
    private final EscPosInvoiceFormatter escPosInvoiceFormatter;
    private final ClinicalPdfReportGenerator clinicalPdfReportGenerator;
    private final ResultEntryService resultEntryService;
    private final String reportVerifyBaseUrl;

    public ZeroClickSpoolerService(PrintAgentWebSocketHandler webSocketHandler,
                                   ZplBarcodeFormatter zplBarcodeFormatter,
                                   EscPosInvoiceFormatter escPosInvoiceFormatter,
                                   ClinicalPdfReportGenerator clinicalPdfReportGenerator,
                                   ResultEntryService resultEntryService,
                                   @Value("${lims.reports.verify-base-url:https://reports.apexpathlab.com}") String reportVerifyBaseUrl) {
        this.webSocketHandler = webSocketHandler;
        this.zplBarcodeFormatter = zplBarcodeFormatter;
        this.escPosInvoiceFormatter = escPosInvoiceFormatter;
        this.clinicalPdfReportGenerator = clinicalPdfReportGenerator;
        this.resultEntryService = resultEntryService;
        this.reportVerifyBaseUrl = reportVerifyBaseUrl;
    }

    /**
     * Spools the thermal invoice and tube labels once the order is committed.
     */
    @ApplicationModuleListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("ZeroClickSpoolerService received OrderCreatedEvent for {}", event.accessionNumber());

        // 1. Spool 80mm ESC/POS Thermal Invoice
        String escPosPayload = escPosInvoiceFormatter.generateEscPosReceipt(event);
        webSocketHandler.dispatchPrintJob(PrintJob.of(
                PrintJob.PrintTarget.INVOICE_POS,
                "text/plain; charset=escpos",
                escPosPayload,
                event.accessionNumber(),
                "Thermal Invoice Receipt - " + event.accessionNumber()
        ));

        // 2. Spool Vacutainer Thermal Barcode Stickers (one per required container tube)
        for (OrderCreatedEvent.SampleBarcodeItemDto sample : event.samples()) {
            String zplPayload = zplBarcodeFormatter.generateZpl(
                    event.uhid(),
                    event.accessionNumber(),
                    event.patientFullName(),
                    sample.containerType(),
                    sample.specimenType(),
                    sample.barcode()
            );
            webSocketHandler.dispatchPrintJob(PrintJob.of(
                    PrintJob.PrintTarget.BARCODE_THERMAL,
                    "text/plain; charset=zpl",
                    zplPayload,
                    event.accessionNumber(),
                    "Vacutainer Label (" + sample.containerType() + ") - " + sample.barcode()
            ));
        }
    }

    /**
     * Spools the signed A4 report once the pathologist's sign-off is committed.
     */
    @ApplicationModuleListener
    public void onOrderAuthorized(OrderAuthorizedEvent event) {
        log.info("ZeroClickSpoolerService received OrderAuthorizedEvent for {}", event.accessionNumber());
        spoolLaserReport(event.orderId());
    }

    public PrintJob spoolLaserReport(UUID orderId) {
        ResultEntryDtos.OrderWithResultsDto reportData = resultEntryService.getOrderResultsWithDemographics(orderId);
        byte[] pdfBytes = clinicalPdfReportGenerator.generateDiagnosticReportPdf(reportData, reportVerifyBaseUrl);

        PrintJob reportJob = PrintJob.of(
                PrintJob.PrintTarget.REPORT_LASER,
                "application/pdf; base64",
                Base64.getEncoder().encodeToString(pdfBytes),
                reportData.accessionNumber(),
                "Diagnostic Clinical Report - " + reportData.accessionNumber()
        );
        webSocketHandler.dispatchPrintJob(reportJob);
        return reportJob;
    }

    public byte[] getReportPdfBytes(UUID orderId) {
        ResultEntryDtos.OrderWithResultsDto reportData = resultEntryService.getOrderResultsWithDemographics(orderId);
        return clinicalPdfReportGenerator.generateDiagnosticReportPdf(reportData, reportVerifyBaseUrl);
    }

    public List<PrintJob> getRecentJobs() {
        return webSocketHandler.getRecentSpoolJobs();
    }

    public int getActiveAgentCount() {
        return webSocketHandler.getConnectedAgentCount();
    }
}
