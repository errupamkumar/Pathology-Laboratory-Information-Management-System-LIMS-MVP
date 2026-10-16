package com.lims.accessioning.dto;

import com.lims.common.enums.RejectionReason;
import com.lims.common.enums.SampleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class AccessioningDtos {

    public static final String BARCODE_REGEX = "^[A-Za-z0-9\\-]{1,64}$";

    public record ScanReceiveRequest(
            @Schema(example = "ACC-2026-5001-S01")
            @NotBlank(message = "Barcode is mandatory")
            @Pattern(regexp = BARCODE_REGEX, message = "Barcode format is invalid")
            String barcode
    ) {}

    public record SampleRejectionRequest(
            @Schema(example = "ACC-2026-5001-S01")
            @NotBlank(message = "Barcode is mandatory")
            @Pattern(regexp = BARCODE_REGEX, message = "Barcode format is invalid")
            String barcode,

            @Schema(example = "HEMOLYZED")
            @NotNull(message = "Rejection reason is mandatory")
            RejectionReason rejectionReason,

            @Schema(example = "Gross hemolysis observed after centrifugation; recollection advised.")
            @Size(max = 1000, message = "Rejection notes must be at most 1000 characters")
            String rejectionNotes
    ) {}

    public record SpecimenSampleDto(
            UUID id,
            UUID orderId,
            String accessionNumber,
            String barcode,
            String containerType,
            String specimenType,
            SampleStatus status,
            RejectionReason rejectionReason,
            String rejectionNotes,
            Instant collectedAt,
            Instant receivedAt,
            Instant createdAt
    ) {}
}
