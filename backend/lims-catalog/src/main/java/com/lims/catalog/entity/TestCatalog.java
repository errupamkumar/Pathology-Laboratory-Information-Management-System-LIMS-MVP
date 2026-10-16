package com.lims.catalog.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_catalogs")
@Auditable
public class TestCatalog extends BaseEntity {

    @NotBlank(message = "Test code is mandatory")
    @Column(name = "test_code", unique = true, nullable = false, length = 50)
    private String testCode;

    @NotBlank(message = "Test name is mandatory")
    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    @NotBlank(message = "Department is mandatory")
    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @NotBlank(message = "Specimen type is mandatory")
    @Column(name = "specimen_type", nullable = false, length = 100)
    private String specimenType;

    @NotBlank(message = "Container type is mandatory")
    @Column(name = "container_type", nullable = false, length = 100)
    private String containerType;

    @NotNull(message = "Price is mandatory")
    @Positive(message = "Price must be positive")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull(message = "TAT minutes is mandatory")
    @Column(name = "tat_minutes", nullable = false)
    private Integer tatMinutes;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "testCatalog", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @JsonManagedReference
    private List<TestParameter> parameters = new ArrayList<>();

    public TestCatalog() {}

    public TestCatalog(UUID id, String testCode, String testName, String department,
                       String specimenType, String containerType, BigDecimal price, Integer tatMinutes) {
        super(id);
        this.testCode = testCode;
        this.testName = testName;
        this.department = department;
        this.specimenType = specimenType;
        this.containerType = containerType;
        this.price = price;
        this.tatMinutes = tatMinutes;
        this.isActive = true;
    }

    public String getTestCode() {
        return testCode;
    }

    public void setTestCode(String testCode) {
        this.testCode = testCode;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSpecimenType() {
        return specimenType;
    }

    public void setSpecimenType(String specimenType) {
        this.specimenType = specimenType;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getTatMinutes() {
        return tatMinutes;
    }

    public void setTatMinutes(Integer tatMinutes) {
        this.tatMinutes = tatMinutes;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public List<TestParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<TestParameter> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(TestParameter parameter) {
        parameters.add(parameter);
        parameter.setTestCatalog(this);
    }
}
