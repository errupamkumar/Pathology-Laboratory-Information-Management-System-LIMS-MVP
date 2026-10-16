package com.lims.diagnostics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ResultEntryDtos {

    public record ParameterResultInput(
            @Schema(example = "b0000000-0000-0000-0000-000000000001")
            @NotNull(message = "Parameter ID is mandatory")
            UUID parameterId,

            @Schema(example = "Hemoglobin (Hb)")
            @NotBlank(message = "Parameter name is mandatory")
            @Size(max = 150, message = "Parameter name must be at most 150 characters")
            String parameterName,

            @Schema(example = "g/dL")
            @Size(max = 50, message = "Unit must be at most 50 characters")
            String unit,

            @Schema(example = "13.4")
            @NotBlank(message = "Observed value is mandatory")
            @Size(max = 100, message = "Observed value must be at most 100 characters")
            String observedValue,

            @Size(max = 1000, message = "Technician notes must be at most 1000 characters")
            String technicianNotes
    ) {}

    public record BatchResultEntryRequest(
            @NotNull(message = "Order ID is mandatory")
            UUID orderId,

            @NotEmpty(message = "At least one result is required")
            @Size(max = 200, message = "At most 200 results can be submitted at once")
            List<@NotNull @Valid ParameterResultInput> results
    ) {}

    public record AuthorizeOrderRequest(
            @NotNull(message = "Order ID is mandatory")
            UUID orderId,

            @Schema(example = "Dr. Rajesh Sharma, MD")
            @NotBlank(message = "Pathologist name is mandatory")
            @Size(max = 150, message = "Pathologist name must be at most 150 characters")
            String pathologistName,

            @Schema(example = "MCI-64921-PATH")
            @NotBlank(message = "License number is mandatory")
            @Size(max = 100, message = "License number must be at most 100 characters")
            String licenseNumber
    ) {}

    public record TestResultDto(
            UUID id,
            UUID orderId,
            UUID parameterId,
            String parameterName,
            String unit,
            String observedValue,
            Boolean isCritical,
            Boolean isOutOfRange,
            Boolean requiresReview,
            BigDecimal deltaChangePercentage,
            String technicianNotes,
            BigDecimal normalLow,
            BigDecimal normalHigh,
            BigDecimal criticalLow,
            BigDecimal criticalHigh,
            String textNormalVal,
            Instant enteredAt
    ) {}

    public record OrderWithResultsDto(
            UUID orderId,
            String accessionNumber,
            String patientUhid,
            String patientName,
            int patientAge,
            String patientGender,
            String referringDoctor,
            String status,
            List<TestResultDto> results,
            AuthorizationInfoDto authorization
    ) {
        public record AuthorizationInfoDto(
                String pathologistName,
                String licenseNumber,
                String digitalSignatureHash,
                String verificationToken,
                String authorizedBy,
                Instant authorizedAt
        ) {}
    }

    /**
     * Response of a successful sign-off.
     */
    public record AuthorizationDto(
            UUID orderId,
            String pathologistName,
            String licenseNumber,
            String digitalSignatureHash,
            String verificationToken,
            String authorizedBy,
            Instant authorizedAt
    ) {}

    /**
     * Public verification result: proves authenticity without exposing patient data or internal IDs.
     */
    public record ReportVerificationDto(
            boolean valid,
            String verificationToken,
            String pathologistName,
            String licenseNumber,
            String digitalSignatureHash,
            Instant authorizedAt
    ) {}
}
