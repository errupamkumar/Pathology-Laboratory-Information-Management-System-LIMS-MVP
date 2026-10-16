package com.lims.diagnostics.entity;

import com.lims.common.audit.Auditable;
import com.lims.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_authorizations", indexes = {
        @Index(name = "idx_authorizations_order", columnList = "order_id", unique = true)
})
@Auditable
public class OrderAuthorization extends BaseEntity {

    @NotNull
    @Column(name = "order_id", unique = true, nullable = false)
    private UUID orderId;

    @NotBlank
    @Column(name = "pathologist_name", nullable = false, length = 150)
    private String pathologistName;

    @NotBlank
    @Column(name = "license_number", nullable = false, length = 100)
    private String licenseNumber;

    @NotBlank
    @Column(name = "digital_signature_hash", nullable = false, length = 255)
    private String digitalSignatureHash;

    @NotBlank
    @Column(name = "verification_token", nullable = false, length = 100)
    private String verificationToken;

    @Column(name = "authorized_at", nullable = false)
    private Instant authorizedAt = Instant.now();

    /**
     * Username of the authenticated account that performed the sign-off (binds the signature to an identity).
     */
    @Column(name = "authorized_by", length = 100)
    private String authorizedBy;

    public OrderAuthorization() {}

    public OrderAuthorization(UUID id, UUID orderId, String pathologistName, String licenseNumber,
                              String digitalSignatureHash, String verificationToken,
                              String authorizedBy, Instant authorizedAt) {
        super(id);
        this.orderId = orderId;
        this.pathologistName = pathologistName;
        this.licenseNumber = licenseNumber;
        this.digitalSignatureHash = digitalSignatureHash;
        this.verificationToken = verificationToken;
        this.authorizedBy = authorizedBy;
        this.authorizedAt = authorizedAt;
    }

    public String getAuthorizedBy() {
        return authorizedBy;
    }

    public void setAuthorizedBy(String authorizedBy) {
        this.authorizedBy = authorizedBy;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getPathologistName() {
        return pathologistName;
    }

    public void setPathologistName(String pathologistName) {
        this.pathologistName = pathologistName;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getDigitalSignatureHash() {
        return digitalSignatureHash;
    }

    public void setDigitalSignatureHash(String digitalSignatureHash) {
        this.digitalSignatureHash = digitalSignatureHash;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public void setAuthorizedAt(Instant authorizedAt) {
        this.authorizedAt = authorizedAt;
    }
}
