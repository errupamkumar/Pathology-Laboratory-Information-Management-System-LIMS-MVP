package com.lims.patient.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lims.common.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Book an order for an existing patient (patientId or uhid) or register a new patient inline")
public record RegisterPatientAndBookOrderRequest(
        // Existing patient (optional)
        UUID patientId,

        @Size(max = 64)
        String uhid,

        // New patient demographics (required when neither patientId nor uhid is supplied)
        @Schema(example = "Rahul")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @Schema(example = "Verma")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @Schema(example = "1988-04-12")
        @Past(message = "Date of birth must be in the past")
        LocalDate dob,

        @Schema(example = "MALE")
        Gender gender,

        @Schema(example = "+91 98765 43210")
        @Pattern(regexp = PatientValidation.PHONE_REGEX, message = "Phone number format is invalid")
        String phone,

        @Schema(example = "rahul.verma@example.com")
        @Email(message = "Email address is invalid")
        @Size(max = 150)
        String email,

        // Order & Billing
        @Schema(example = "[\"a0000000-0000-0000-0000-000000000001\"]")
        @NotEmpty(message = "At least one test must be selected")
        @Size(max = 50, message = "At most 50 tests can be booked on one order")
        List<@NotNull UUID> testCatalogIds,

        @Schema(example = "0.00")
        @DecimalMin(value = "0.00", message = "Discount cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Discount must have at most 2 decimal places")
        BigDecimal discountAmount,

        @Schema(example = "450.00")
        @NotNull(message = "Paid amount is required")
        @DecimalMin(value = "0.00", message = "Paid amount cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Paid amount must have at most 2 decimal places")
        BigDecimal paidAmount,

        @Schema(example = "Dr. A. Sharma")
        @Size(max = 150, message = "Referring doctor must be at most 150 characters")
        String referringDoctor
) {

    @JsonIgnore
    public boolean isExistingPatient() {
        return patientId != null || (uhid != null && !uhid.isBlank());
    }

    /**
     * Cross-field rule: a new registration needs complete demographics.
     */
    @JsonIgnore
    @AssertTrue(message = "Patient demographics (firstName, lastName, dob, gender, phone) are required for new registration")
    public boolean isPatientIdentified() {
        if (isExistingPatient()) {
            return true;
        }
        return notBlank(firstName) && notBlank(lastName) && dob != null && gender != null && notBlank(phone);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
