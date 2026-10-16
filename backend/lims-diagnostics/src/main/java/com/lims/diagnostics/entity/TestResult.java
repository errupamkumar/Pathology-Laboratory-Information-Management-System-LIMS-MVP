package com.lims.diagnostics.entity;

import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_results", indexes = {
        @Index(name = "idx_test_results_order", columnList = "order_id"),
        @Index(name = "idx_test_results_param", columnList = "parameter_id")
})
@Auditable
public class TestResult extends BaseEntity {

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotNull
    @Column(name = "parameter_id", nullable = false)
    private UUID parameterId;

    @NotBlank
    @Column(name = "parameter_name", nullable = false, length = 150)
    private String parameterName;

    @Column(name = "unit", length = 50)
    private String unit;

    @NotBlank
    @Column(name = "observed_value", nullable = false, length = 100)
    private String observedValue;

    @NotNull
    @Column(name = "is_critical", nullable = false)
    private Boolean isCritical = false;

    @NotNull
    @Column(name = "is_out_of_range", nullable = false)
    private Boolean isOutOfRange = false;

    @NotNull
    @Column(name = "requires_review", nullable = false)
    private Boolean requiresReview = false;

    @Column(name = "delta_change_percentage", precision = 8, scale = 2)
    private BigDecimal deltaChangePercentage;

    @Column(name = "technician_notes", columnDefinition = "TEXT")
    private String technicianNotes;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt = Instant.now();

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    public TestResult() {}

    public TestResult(UUID id, UUID orderId, UUID parameterId, String parameterName,
                      String unit, String observedValue) {
        super(id);
        this.orderId = orderId;
        this.parameterId = parameterId;
        this.parameterName = parameterName;
        this.unit = unit;
        this.observedValue = observedValue;
        this.isCritical = false;
        this.isOutOfRange = false;
        this.requiresReview = false;
        this.enteredAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getParameterId() {
        return parameterId;
    }

    public void setParameterId(UUID parameterId) {
        this.parameterId = parameterId;
    }

    public String getParameterName() {
        return parameterName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getObservedValue() {
        return observedValue;
    }

    public void setObservedValue(String observedValue) {
        this.observedValue = observedValue;
    }

    public Boolean getIsCritical() {
        return isCritical;
    }

    public void setIsCritical(Boolean critical) {
        isCritical = critical;
    }

    public Boolean getIsOutOfRange() {
        return isOutOfRange;
    }

    public void setIsOutOfRange(Boolean outOfRange) {
        isOutOfRange = outOfRange;
    }

    public Boolean getRequiresReview() {
        return requiresReview;
    }

    public void setRequiresReview(Boolean requiresReview) {
        this.requiresReview = requiresReview;
    }

    public BigDecimal getDeltaChangePercentage() {
        return deltaChangePercentage;
    }

    public void setDeltaChangePercentage(BigDecimal deltaChangePercentage) {
        this.deltaChangePercentage = deltaChangePercentage;
    }

    public String getTechnicianNotes() {
        return technicianNotes;
    }

    public void setTechnicianNotes(String technicianNotes) {
        this.technicianNotes = technicianNotes;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Instant enteredAt) {
        this.enteredAt = enteredAt;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
