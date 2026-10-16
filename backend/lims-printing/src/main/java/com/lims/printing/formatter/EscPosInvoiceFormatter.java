package com.lims.printing.formatter;

import com.lims.common.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
public class EscPosInvoiceFormatter {

    private static final String ESC = "\u001B";
    private static final String GS  = "\u001D";

    public String generateEscPosReceipt(OrderCreatedEvent event) {
        StringBuilder sb = new StringBuilder();

        // Initialize printer
        sb.append(ESC).append("@");

        // Center Align
        sb.append(ESC).append("a").append("\u0001");

        // Double height & width bold header
        sb.append(ESC).append("!").append("\u0038");
        sb.append("APEX CLINICAL PATHOLOGY\n");
        sb.append(ESC).append("!").append("\u0000"); // Normal
        sb.append("NABL Accredited Laboratory | ISO 15189:2022\n");
        sb.append("Tel: +91 98765 43210 | support@apexpathlab.com\n");
        sb.append("================================================\n");

        // Left Align
        sb.append(ESC).append("a").append("\u0000");

        sb.append("INVOICE / BILL OF SUPPLY\n");
        sb.append("Date:       ").append(DateTimeFormatter.ISO_INSTANT.format(event.occurredAt())).append("\n");
        sb.append("Accession:  ").append(event.accessionNumber()).append("\n");
        sb.append("UHID:       ").append(event.uhid()).append("\n");
        sb.append("Patient:    ").append(event.patientFullName()).append(" (")
                .append(event.patientAge()).append("Y / ").append(event.patientGender()).append(")\n");
        sb.append("Phone:      ").append(event.patientPhone()).append("\n");
        sb.append("Ref Doctor: ").append(event.referringDoctor()).append("\n");
        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-4s %-28s %12s\n", "#", "Test Name", "Price (INR)"));
        sb.append("------------------------------------------------\n");

        int idx = 1;
        for (OrderCreatedEvent.OrderTestItemDto item : event.items()) {
            String name = item.testName().length() > 27 ? item.testName().substring(0, 27) : item.testName();
            sb.append(String.format("%-4d %-28s %12.2f\n", idx++, name, item.price()));
        }

        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-32s %14.2f\n", "TOTAL AMOUNT:", event.totalAmount()));
        sb.append(String.format("%-32s %14.2f\n", "PAID AMOUNT:", event.paidAmount()));

        BigDecimal balance = event.totalAmount().subtract(event.paidAmount()).max(BigDecimal.ZERO);
        sb.append(String.format("%-32s %14.2f\n", "BALANCE DUE:", balance));
        sb.append("================================================\n");

        // Center Align for Footer
        sb.append(ESC).append("a").append("\u0001");
        sb.append("Online Reports: https://reports.apexpathlab.com\n");
        sb.append("Please bring this receipt for physical collection.\n");
        sb.append("*** THANK YOU - GET WELL SOON ***\n\n\n");

        // Partial cut command
        sb.append(GS).append("V").append("\u0041").append("\u0003");

        return sb.toString();
    }
}
