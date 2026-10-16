package com.lims.printing.service;

import com.lims.common.event.OrderCreatedEvent;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lims.diagnostics.service.ResultEntryService;
import com.lims.printing.formatter.EscPosInvoiceFormatter;
import com.lims.printing.formatter.ZplBarcodeFormatter;
import com.lims.printing.model.PrintJob;
import com.lims.printing.pdf.ClinicalPdfReportGenerator;
import com.lims.printing.websocket.PrintAgentWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZeroClickSpoolerServiceTest {

    @Mock PrintAgentWebSocketHandler webSocketHandler;
    @Mock ResultEntryService resultEntryService;
    @Mock ClinicalPdfReportGenerator pdfGenerator;

    ZeroClickSpoolerService service;

    @BeforeEach
    void setUp() {
        service = new ZeroClickSpoolerService(webSocketHandler, new ZplBarcodeFormatter(), new EscPosInvoiceFormatter(),
                pdfGenerator, resultEntryService, "https://verify.example");
    }

    @Test
    void orderCreatedSpoolsOneInvoiceAndOneLabelPerTube() {
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), "ACC-2026-5001", UUID.randomUUID(),
                "UHID-2026-1001", "Sarah Jenkins", "+91 98112 34567", "FEMALE", 44,
                new BigDecimal("570.00"), new BigDecimal("570.00"), "Self",
                List.of(new OrderCreatedEvent.OrderTestItemDto(UUID.randomUUID(), "CBC", "Complete Blood Count",
                        new BigDecimal("450.00"), "EDTA")),
                List.of(new OrderCreatedEvent.SampleBarcodeItemDto("ACC-2026-5001-S01", "EDTA", "Whole Blood"),
                        new OrderCreatedEvent.SampleBarcodeItemDto("ACC-2026-5001-S02", "Fluoride", "Plasma")),
                null);

        service.onOrderCreated(event);

        ArgumentCaptor<PrintJob> jobs = ArgumentCaptor.forClass(PrintJob.class);
        verify(webSocketHandler, times(3)).dispatchPrintJob(jobs.capture());
        assertThat(jobs.getAllValues()).extracting(PrintJob::target).containsExactly(
                PrintJob.PrintTarget.INVOICE_POS, PrintJob.PrintTarget.BARCODE_THERMAL, PrintJob.PrintTarget.BARCODE_THERMAL);
        assertThat(jobs.getAllValues().get(1).payload()).contains("^FDACC-2026-5001-S01^FS");
    }

    @Test
    void laserReportIsBase64Pdf() {
        UUID orderId = UUID.randomUUID();
        ResultEntryDtos.OrderWithResultsDto data = new ResultEntryDtos.OrderWithResultsDto(orderId, "ACC-2026-5001",
                "UHID-2026-1001", "Sarah Jenkins", 44, "FEMALE", "Self", "AUTHORIZED", List.of(), null);
        when(resultEntryService.getOrderResultsWithDemographics(orderId)).thenReturn(data);
        when(pdfGenerator.generateDiagnosticReportPdf(data, "https://verify.example")).thenReturn(new byte[]{'%', 'P', 'D', 'F'});

        PrintJob job = service.spoolLaserReport(orderId);

        assertThat(job.target()).isEqualTo(PrintJob.PrintTarget.REPORT_LASER);
        assertThat(Base64.getDecoder().decode(job.payload())).startsWith('%', 'P', 'D', 'F');
        verify(webSocketHandler).dispatchPrintJob(job);
    }
}
