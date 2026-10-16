package com.lims.accessioning.entity;

import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import com.lims.common.enums.RejectionReason;
import com.lims.common.enums.SampleStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "specimen_samples", indexes = {
        @Index(name = "idx_samples_barcode", columnList = "barcode", unique = true),
        @Index(name = "idx_samples_order", columnList = "order_id"),
        @Index(name = "idx_samples_status", columnList = "status")
})
@Auditable
public class SpecimenSample extends BaseEntity {

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotBlank
    @Column(name = "accession_number", nullable = false, length = 64)
    private String accessionNumber;

    @NotBlank
    @Column(name = "barcode", unique = true, nullable = false, length = 64)
    private String barcode;

    @NotBlank
    @Column(name = "container_type", nullable = false, length = 100)
    private String containerType;

    @NotBlank
    @Column(name = "specimen_type", nullable = false, length = 100)
    private String specimenType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SampleStatus status = SampleStatus.COLLECTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 100)
    private RejectionReason rejectionReason;

    @Column(name = "rejection_notes", columnDefinition = "TEXT")
    private String rejectionNotes;

    @Column(name = "collected_at")
    private Instant collectedAt = Instant.now();

    @Column(name = "received_at")
    private Instant receivedAt;

    public SpecimenSample() {}

    public SpecimenSample(UUID id, UUID orderId, String accessionNumber, String barcode,
                          String containerType, String specimenType) {
        super(id);
        this.orderId = orderId;
        this.accessionNumber = accessionNumber;
        this.barcode = barcode;
        this.containerType = containerType;
        this.specimenType = specimenType;
        this.status = SampleStatus.COLLECTED;
        this.collectedAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    public String getSpecimenType() {
        return specimenType;
    }

    public void setSpecimenType(String specimenType) {
        this.specimenType = specimenType;
    }

    public SampleStatus getStatus() {
        return status;
    }

    public void setStatus(SampleStatus status) {
        this.status = status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(RejectionReason rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getRejectionNotes() {
        return rejectionNotes;
    }

    public void setRejectionNotes(String rejectionNotes) {
        this.rejectionNotes = rejectionNotes;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
