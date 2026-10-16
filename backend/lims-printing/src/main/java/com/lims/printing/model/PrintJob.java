package com.lims.printing.model;

import java.time.Instant;
import java.util.UUID;

public record PrintJob(
        String jobId,
        PrintTarget target,
        String contentType,
        String payload,
        String accessionNumber,
        String title,
        Instant queuedAt
) {
    public enum PrintTarget {
        BARCODE_THERMAL,
        INVOICE_POS,
        REPORT_LASER
    }

    public static PrintJob of(PrintTarget target, String contentType, String payload,
                              String accessionNumber, String title) {
        return new PrintJob(
                UUID.randomUUID().toString(),
                target,
                contentType,
                payload,
                accessionNumber,
                title,
                Instant.now()
        );
    }
}
