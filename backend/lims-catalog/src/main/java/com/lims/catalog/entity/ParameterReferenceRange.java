package com.lims.catalog.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import com.lims.common.enums.Gender;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "parameter_reference_ranges")
@Auditable
public class ParameterReferenceRange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", nullable = false)
    @JsonBackReference
    private TestParameter parameter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender = Gender.ALL;

    @NotNull
    @Column(name = "min_age", nullable = false)
    private Integer minAge = 0;

    @NotNull
    @Column(name = "max_age", nullable = false)
    private Integer maxAge = 150;

    @Column(name = "normal_low", precision = 10, scale = 3)
    private BigDecimal normalLow;

    @Column(name = "normal_high", precision = 10, scale = 3)
    private BigDecimal normalHigh;

    @Column(name = "critical_low", precision = 10, scale = 3)
    private BigDecimal criticalLow;

    @Column(name = "critical_high", precision = 10, scale = 3)
    private BigDecimal criticalHigh;

    @Column(name = "text_normal_val", length = 255)
    private String textNormalVal;

    public ParameterReferenceRange() {}

    public ParameterReferenceRange(UUID id, Gender gender, Integer minAge, Integer maxAge,
                                   BigDecimal normalLow, BigDecimal normalHigh,
                                   BigDecimal criticalLow, BigDecimal criticalHigh,
                                   String textNormalVal) {
        super(id);
        this.gender = gender;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.normalLow = normalLow;
        this.normalHigh = normalHigh;
        this.criticalLow = criticalLow;
        this.criticalHigh = criticalHigh;
        this.textNormalVal = textNormalVal;
    }

    public TestParameter getParameter() {
        return parameter;
    }

    public void setParameter(TestParameter parameter) {
        this.parameter = parameter;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public BigDecimal getNormalLow() {
        return normalLow;
    }

    public void setNormalLow(BigDecimal normalLow) {
        this.normalLow = normalLow;
    }

    public BigDecimal getNormalHigh() {
        return normalHigh;
    }

    public void setNormalHigh(BigDecimal normalHigh) {
        this.normalHigh = normalHigh;
    }

    public BigDecimal getCriticalLow() {
        return criticalLow;
    }

    public void setCriticalLow(BigDecimal criticalLow) {
        this.criticalLow = criticalLow;
    }

    public BigDecimal getCriticalHigh() {
        return criticalHigh;
    }

    public void setCriticalHigh(BigDecimal criticalHigh) {
        this.criticalHigh = criticalHigh;
    }

    public String getTextNormalVal() {
        return textNormalVal;
    }

    public void setTextNormalVal(String textNormalVal) {
        this.textNormalVal = textNormalVal;
    }
}
