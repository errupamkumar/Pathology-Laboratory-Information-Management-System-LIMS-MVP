package com.lims.printing.pdf;

import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalPdfReportGeneratorTest {

    private final ClinicalPdfReportGenerator generator = new ClinicalPdfReportGenerator();

    @Test
    void unsignedReportIsMarkedPreliminaryAndCarriesNoSignature() throws IOException {
        String text = extractText(generator.generateDiagnosticReportPdf(report(null), "https://verify.example"));

        assertThat(text).contains("PRELIMINARY REPORT").contains("Not valid for clinical use");
        assertThat(text).doesNotContain("DIGITALLY SIGNED");
    }

    @Test
    void signedReportShowsPathologistAndReferringDoctor() throws IOException {
        ResultEntryDtos.OrderWithResultsDto.AuthorizationInfoDto auth = new ResultEntryDtos.OrderWithResultsDto.AuthorizationInfoDto(
                "Dr. Rajesh Sharma, MD", "MCI-64921-PATH", "a".repeat(64), "VERIFY-0011223344556677", "pathologist", Instant.now());

        String text = extractText(generator.generateDiagnosticReportPdf(report(auth), "https://verify.example"));

        assertThat(text).contains("DIGITALLY SIGNED").contains("Dr. Rajesh Sharma, MD").contains("Dr. A. Sharma");
        assertThat(text).doesNotContain("PRELIMINARY");
    }

    private static ResultEntryDtos.OrderWithResultsDto report(ResultEntryDtos.OrderWithResultsDto.AuthorizationInfoDto auth) {
        ResultEntryDtos.TestResultDto hb = new ResultEntryDtos.TestResultDto(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Hemoglobin (Hb)", "g/dL", "5.1", true, true, false, null, null,
                new BigDecimal("12.0"), new BigDecimal("15.5"), new BigDecimal("7.0"), new BigDecimal("20.0"), null, Instant.now());
        return new ResultEntryDtos.OrderWithResultsDto(UUID.randomUUID(), "ACC-2026-5001", "UHID-2026-1001",
                "Sarah Jenkins", 44, "FEMALE", "Dr. A. Sharma", auth != null ? "AUTHORIZED" : "COMPLETED", List.of(hb), auth);
    }

    private static String extractText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }
}
