package com.lims.printing.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ClinicalPdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClinicalPdfReportGenerator.class);

    // Primary Brand Accent: #23408e
    private static final Color COLOR_PRIMARY = new Color(35, 64, 142);
    // Interactive Secondary: #385399
    private static final Color COLOR_SECONDARY = new Color(56, 83, 153);
    // Critical / Alert / Panic: #ed1b24
    private static final Color COLOR_CRITICAL = new Color(237, 27, 36);
    // Border / Gridlines: #cccccc
    private static final Color COLOR_BORDER = new Color(204, 204, 204);
    // Background light gray
    private static final Color COLOR_LIGHT_BG = new Color(245, 247, 250);

    private static final Font FONT_HEADER_TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, COLOR_PRIMARY);
    private static final Font FONT_HEADER_SUB = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
    private static final Font FONT_SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_PRIMARY);
    private static final Font FONT_LABEL = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font FONT_VALUE = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font FONT_TH = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font FONT_TD = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font FONT_TD_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font FONT_CRITICAL = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_CRITICAL);

    public byte[] generateDiagnosticReportPdf(ResultEntryDtos.OrderWithResultsDto reportData, String baseUrl) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // 1. Lab Header Banner
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{70, 30});

            PdfPCell labInfoCell = new PdfPCell();
            labInfoCell.setBorder(Rectangle.NO_BORDER);
            labInfoCell.addElement(new Paragraph("APEX CLINICAL REFERENCE LAB", FONT_HEADER_TITLE));
            labInfoCell.addElement(new Paragraph("NABL Accredited Quality Lab (ISO 15189:2022) | CAP Certified", FONT_HEADER_SUB));
            labInfoCell.addElement(new Paragraph("100 Healthcare Blvd, Medical Enclave | Tel: +91 98765 43210 | www.apexpathlab.com", FONT_HEADER_SUB));
            headerTable.addCell(labInfoCell);

            PdfPCell badgeCell = new PdfPCell();
            badgeCell.setBorder(Rectangle.NO_BORDER);
            badgeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph badgePara = new Paragraph("CLINICAL DIAGNOSTIC\nFINAL REPORT", FONT_SECTION);
            badgePara.setAlignment(Element.ALIGN_RIGHT);
            badgeCell.addElement(badgePara);
            headerTable.addCell(badgeCell);

            document.add(headerTable);

            // Divider bar
            PdfPTable divider = new PdfPTable(1);
            divider.setWidthPercentage(100);
            divider.setSpacingBefore(8);
            divider.setSpacingAfter(8);
            PdfPCell dividerCell = new PdfPCell();
            dividerCell.setBackgroundColor(COLOR_PRIMARY);
            dividerCell.setFixedHeight(2.5f);
            dividerCell.setBorder(Rectangle.NO_BORDER);
            divider.addCell(dividerCell);
            document.add(divider);

            // 2. Patient Demographics & Order Metadata Box
            PdfPTable demoTable = new PdfPTable(4);
            demoTable.setWidthPercentage(100);
            demoTable.setWidths(new float[]{22, 28, 22, 28});
            demoTable.getDefaultCell().setPadding(4);

            addDemoField(demoTable, "Patient Name:", reportData.patientName());
            addDemoField(demoTable, "UHID / MRN:", reportData.patientUhid());
            addDemoField(demoTable, "Age / Gender:", reportData.patientAge() + " Y / " + reportData.patientGender());
            addDemoField(demoTable, "Accession No:", reportData.accessionNumber());
            addDemoField(demoTable, "Ref. Physician:", reportData.referringDoctor() != null ? reportData.referringDoctor() : "Self / Walk-in");
            addDemoField(demoTable, "Order Status:", reportData.status());

            String reportDateStr = (reportData.authorization() != null && reportData.authorization().authorizedAt() != null)
                    ? DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZoneId.systemDefault()).format(reportData.authorization().authorizedAt())
                    : "PENDING AUTHORIZATION";
            addDemoField(demoTable, "Report Released:", reportDateStr);
            addDemoField(demoTable, "Specimen Status:", "Sample Verified");

            document.add(demoTable);
            document.add(new Paragraph(" ", new Font(Font.HELVETICA, 6)));

            // 3. Clinical Results Table
            PdfPTable resultsTable = new PdfPTable(5);
            resultsTable.setWidthPercentage(100);
            resultsTable.setWidths(new float[]{34, 16, 14, 22, 14});
            resultsTable.setHeaderRows(1);

            // Table Header
            addTableHeader(resultsTable, "TEST PARAMETER");
            addTableHeader(resultsTable, "OBSERVED");
            addTableHeader(resultsTable, "UNIT");
            addTableHeader(resultsTable, "REF. INTERVAL");
            addTableHeader(resultsTable, "FLAG / STATUS");

            for (ResultEntryDtos.TestResultDto res : reportData.results()) {
                boolean isAbnormal = Boolean.TRUE.equals(res.isOutOfRange()) || Boolean.TRUE.equals(res.isCritical());

                // Parameter name
                PdfPCell nameCell = new PdfPCell(new Phrase(res.parameterName(), FONT_TD_BOLD));
                nameCell.setBorderColor(COLOR_BORDER);
                nameCell.setPadding(5);
                resultsTable.addCell(nameCell);

                // Observed value (bold red #ed1b24 if panic / out of range)
                Font valFont = isAbnormal ? FONT_CRITICAL : FONT_TD_BOLD;
                PdfPCell valCell = new PdfPCell(new Phrase(res.observedValue(), valFont));
                valCell.setBorderColor(COLOR_BORDER);
                valCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                valCell.setPadding(5);
                if (Boolean.TRUE.equals(res.isCritical())) {
                    valCell.setBackgroundColor(new Color(255, 235, 235));
                }
                resultsTable.addCell(valCell);

                // Unit
                PdfPCell unitCell = new PdfPCell(new Phrase(res.unit() != null ? res.unit() : "-", FONT_TD));
                unitCell.setBorderColor(COLOR_BORDER);
                unitCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                unitCell.setPadding(5);
                resultsTable.addCell(unitCell);

                // Ref Interval
                String refInterval = formatRefRange(res.normalLow(), res.normalHigh(), res.textNormalVal());
                PdfPCell refCell = new PdfPCell(new Phrase(refInterval, FONT_TD));
                refCell.setBorderColor(COLOR_BORDER);
                refCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                refCell.setPadding(5);
                resultsTable.addCell(refCell);

                // Flag
                String flag = "NORMAL";
                Font flagFont = FONT_TD;
                if (Boolean.TRUE.equals(res.isCritical())) {
                    flag = "CRITICAL";
                    flagFont = FONT_CRITICAL;
                } else if (Boolean.TRUE.equals(res.isOutOfRange())) {
                    flag = "ABNORMAL";
                    flagFont = FONT_CRITICAL;
                }

                PdfPCell flagCell = new PdfPCell(new Phrase(flag, flagFont));
                flagCell.setBorderColor(COLOR_BORDER);
                flagCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                flagCell.setPadding(5);
                resultsTable.addCell(flagCell);
            }

            document.add(resultsTable);

            // 4. Notes & End of Report
            Paragraph endPara = new Paragraph("\n*** End of Diagnostic Laboratory Examination ***\n", FONT_HEADER_SUB);
            endPara.setAlignment(Element.ALIGN_CENTER);
            document.add(endPara);

            // 5. Verification QR Code & Doctor Authorization Footer
            PdfPTable footerTable = new PdfPTable(2);
            footerTable.setWidthPercentage(100);
            footerTable.setWidths(new float[]{40, 60});
            footerTable.setSpacingBefore(15);

            // QR Code Column
            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.NO_BORDER);

            String verifyToken = reportData.authorization() != null ? reportData.authorization().verificationToken() : "DRAFT";
            String verifyUrl = (baseUrl != null ? baseUrl : "https://reports.apexpathlab.com") + "/verify/" + verifyToken;
            byte[] qrBytes = generateQrCodeImage(verifyUrl, 100, 100);

            if (qrBytes != null) {
                Image qrImage = Image.getInstance(qrBytes);
                qrImage.scaleToFit(75, 75);
                qrCell.addElement(qrImage);
            }
            Paragraph qrNote = new Paragraph("Scan QR code to verify report authenticity\nToken: " + verifyToken, FONT_HEADER_SUB);
            qrCell.addElement(qrNote);
            footerTable.addCell(qrCell);

            // Doctor Sign-off Stamp Column
            PdfPCell signCell = new PdfPCell();
            signCell.setBorder(Rectangle.NO_BORDER);
            signCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

            Paragraph signPara = new Paragraph();
            signPara.setAlignment(Element.ALIGN_RIGHT);
            if (reportData.authorization() != null) {
                String signHash = reportData.authorization().digitalSignatureHash();
                signPara.add(new Phrase("[DIGITALLY SIGNED & VERIFIED]\n", FONT_SECTION));
                signPara.add(new Phrase(reportData.authorization().pathologistName() + "\n", FONT_TD_BOLD));
                signPara.add(new Phrase("Consultant Pathologist & Lab Director\nReg. No: " + reportData.authorization().licenseNumber() + "\n", FONT_TD));
                signPara.add(new Phrase("SHA-256: " + signHash.substring(0, Math.min(24, signHash.length())) + "...\n", FONT_HEADER_SUB));
            } else {
                // An unsigned report must never carry a signature block
                signPara.add(new Phrase("PRELIMINARY REPORT\n", FONT_CRITICAL));
                signPara.add(new Phrase("Pending pathologist authorization.\nNot valid for clinical use.\n", FONT_TD));
            }
            signCell.addElement(signPara);
            footerTable.addCell(signCell);

            document.add(footerTable);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate clinical PDF report for order {}: {}", reportData.accessionNumber(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failure: " + e.getMessage(), e);
        }
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TH));
        cell.setBackgroundColor(COLOR_PRIMARY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        cell.setBorderColor(COLOR_BORDER);
        table.addCell(cell);
    }

    private void addDemoField(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_LABEL));
        labelCell.setBackgroundColor(COLOR_LIGHT_BG);
        labelCell.setBorderColor(COLOR_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "-", FONT_VALUE));
        valueCell.setBorderColor(COLOR_BORDER);
        table.addCell(valueCell);
    }

    private String formatRefRange(BigDecimal low, BigDecimal high, String textVal) {
        if (textVal != null && !textVal.isBlank()) {
            return textVal;
        }
        if (low != null && high != null) {
            return low + " - " + high;
        } else if (low != null) {
            return "> " + low;
        } else if (high != null) {
            return "< " + high;
        }
        return "N/A";
    }

    private byte[] generateQrCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);
            return pngOut.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to generate QR code image: {}", e.getMessage());
            return null;
        }
    }
}
