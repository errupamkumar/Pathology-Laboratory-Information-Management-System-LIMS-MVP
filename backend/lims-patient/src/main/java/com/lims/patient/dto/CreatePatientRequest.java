package com.lims.patient.dto;

import com.lims.common.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePatientRequest(
        @Schema(example = "Rahul")
        @NotBlank(message = "First name is mandatory")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @Schema(example = "Verma")
        @NotBlank(message = "Last name is mandatory")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @Schema(example = "1988-04-12")
        @NotNull(message = "Date of birth is mandatory")
        @Past(message = "Date of birth must be in the past")
        LocalDate dob,

        @Schema(example = "MALE")
        @NotNull(message = "Gender is mandatory")
        Gender gender,

        @Schema(example = "+91 98765 43210")
        @NotBlank(message = "Phone number is mandatory")
        @Pattern(regexp = PatientValidation.PHONE_REGEX, message = "Phone number format is invalid")
        String phone,

        @Schema(example = "rahul.verma@example.com")
        @Email(message = "Email address is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email
) {}
