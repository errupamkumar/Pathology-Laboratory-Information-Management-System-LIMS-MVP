package com.lims.patient.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id")
})
@Auditable
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private DiagnosticOrder order;

    @NotNull
    @Column(name = "test_catalog_id", nullable = false)
    private UUID testCatalogId;

    @NotBlank
    @Column(name = "test_code", nullable = false, length = 50)
    private String testCode;

    @NotBlank
    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    @NotNull
    @Positive
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    public OrderItem() {}

    public OrderItem(UUID id, UUID testCatalogId, String testCode, String testName, BigDecimal price) {
        super(id);
        this.testCatalogId = testCatalogId;
        this.testCode = testCode;
        this.testName = testName;
        this.price = price;
    }

    public DiagnosticOrder getOrder() {
        return order;
    }

    public void setOrder(DiagnosticOrder order) {
        this.order = order;
    }

    public UUID getTestCatalogId() {
        return testCatalogId;
    }

    public void setTestCatalogId(UUID testCatalogId) {
        this.testCatalogId = testCatalogId;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
