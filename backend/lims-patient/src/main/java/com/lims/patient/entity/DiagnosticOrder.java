package com.lims.patient.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import com.lims.common.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_orders", indexes = {
        @Index(name = "idx_orders_accession", columnList = "accession_number", unique = true),
        @Index(name = "idx_orders_patient", columnList = "patient_id"),
        @Index(name = "idx_orders_status", columnList = "status")
})
@Auditable
public class DiagnosticOrder extends BaseEntity {

    @NotBlank(message = "Accession number is mandatory")
    @Column(name = "accession_number", unique = true, nullable = false, length = 64)
    private String accessionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status = OrderStatus.REGISTERED;

    @NotNull
    @PositiveOrZero
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @NotNull
    @PositiveOrZero
    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "referring_doctor", length = 150)
    private String referringDoctor;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    public DiagnosticOrder() {}

    public DiagnosticOrder(UUID id, String accessionNumber, Patient patient,
                           BigDecimal totalAmount, BigDecimal paidAmount, String referringDoctor) {
        super(id);
        this.accessionNumber = accessionNumber;
        this.patient = patient;
        this.status = OrderStatus.REGISTERED;
        this.totalAmount = totalAmount;
        this.paidAmount = paidAmount;
        this.referringDoctor = referringDoctor;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getReferringDoctor() {
        return referringDoctor;
    }

    public void setReferringDoctor(String referringDoctor) {
        this.referringDoctor = referringDoctor;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
