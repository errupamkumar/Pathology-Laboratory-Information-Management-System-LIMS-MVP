package com.lims.catalog.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_parameters")
@Auditable
public class TestParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_catalog_id", nullable = false)
    @JsonBackReference
    private TestCatalog testCatalog;

    @NotBlank(message = "Parameter name is mandatory")
    @Column(name = "parameter_name", nullable = false, length = 150)
    private String parameterName;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "formula", length = 255)
    private String formula;

    @NotNull
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "parameter", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ParameterReferenceRange> referenceRanges = new ArrayList<>();

    public TestParameter() {}

    public TestParameter(UUID id, String parameterName, String unit, String formula, Integer displayOrder) {
        super(id);
        this.parameterName = parameterName;
        this.unit = unit;
        this.formula = formula;
        this.displayOrder = displayOrder;
    }

    public TestCatalog getTestCatalog() {
        return testCatalog;
    }

    public void setTestCatalog(TestCatalog testCatalog) {
        this.testCatalog = testCatalog;
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

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public List<ParameterReferenceRange> getReferenceRanges() {
        return referenceRanges;
    }

    public void setReferenceRanges(List<ParameterReferenceRange> referenceRanges) {
        this.referenceRanges = referenceRanges;
    }

    public void addReferenceRange(ParameterReferenceRange range) {
        referenceRanges.add(range);
        range.setParameter(this);
    }
}
