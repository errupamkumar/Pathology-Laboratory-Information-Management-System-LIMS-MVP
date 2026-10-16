package com.lims.diagnostics.service;

import com.lims.common.api.ClinicalOrderGateway;
import com.lims.common.api.OrderSnapshot;
import com.lims.common.enums.OrderStatus;
import com.lims.common.event.OrderAuthorizedEvent;
import com.lims.common.exception.InvalidOperationException;
import com.lims.common.exception.ResourceNotFoundException;
import com.lims.common.security.CurrentUser;
import com.lims.diagnostics.dto.ResultEntryDtos;
import com.lims.diagnostics.entity.OrderAuthorization;
import com.lims.diagnostics.repository.OrderAuthorizationRepository;
import com.lims.diagnostics.repository.TestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DoctorSignOffService {

    private static final Logger log = LoggerFactory.getLogger(DoctorSignOffService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClinicalOrderGateway orderGateway;
    private final TestResultRepository testResultRepository;
    private final OrderAuthorizationRepository orderAuthorizationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DoctorSignOffService(ClinicalOrderGateway orderGateway,
                                TestResultRepository testResultRepository,
                                OrderAuthorizationRepository orderAuthorizationRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.orderGateway = orderGateway;
        this.testResultRepository = testResultRepository;
        this.orderAuthorizationRepository = orderAuthorizationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Signs the report. Idempotent: re-authorizing returns the existing signature. Concurrent
     * double sign-off is prevented by the unique constraint on order_authorizations.order_id.
     */
    public ResultEntryDtos.AuthorizationDto authorizeOrder(UUID orderId, String pathologistName, String licenseNumber) {
        OrderSnapshot order = orderGateway.getOrder(orderId);

        Optional<OrderAuthorization> existing = orderAuthorizationRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        if (testResultRepository.findByOrderId(orderId).isEmpty()) {
            throw new InvalidOperationException("Cannot authorize order " + order.accessionNumber()
                    + " because no diagnostic test results have been recorded.");
        }

        String signer = CurrentUser.username();
        Instant authorizedAt = Instant.now();
        String verificationToken = "VERIFY-" + randomToken();
        String payloadToSign = String.join("|",
                order.orderId().toString(),
                order.accessionNumber(),
                order.patientUhid(),
                pathologistName.trim(),
                licenseNumber.trim(),
                signer,
                String.valueOf(authorizedAt.toEpochMilli())
        );

        OrderAuthorization authorization = new OrderAuthorization(
                UUID.randomUUID(),
                order.orderId(),
                pathologistName.trim(),
                licenseNumber.trim(),
                sha256(payloadToSign),
                verificationToken,
                signer,
                authorizedAt
        );
        OrderAuthorization savedAuth = orderAuthorizationRepository.save(authorization);

        orderGateway.updateStatus(order.orderId(), OrderStatus.AUTHORIZED);

        log.info("Order {} AUTHORIZED by {} (account: {}, licence: {}) with verification token {}",
                order.accessionNumber(), pathologistName, signer, licenseNumber, verificationToken);

        // Triggers zero-click silent printing of the report after commit
        eventPublisher.publishEvent(new OrderAuthorizedEvent(
                order.orderId(),
                order.accessionNumber(),
                order.patientId(),
                savedAuth.getPathologistName(),
                savedAuth.getLicenseNumber(),
                verificationToken,
                savedAuth.getAuthorizedAt()
        ));

        return toDto(savedAuth);
    }

    @Transactional(readOnly = true)
    public ResultEntryDtos.ReportVerificationDto verify(String verificationToken) {
        return orderAuthorizationRepository.findByVerificationToken(verificationToken.trim())
                .map(a -> new ResultEntryDtos.ReportVerificationDto(
                        true,
                        a.getVerificationToken(),
                        a.getPathologistName(),
                        a.getLicenseNumber(),
                        a.getDigitalSignatureHash(),
                        a.getAuthorizedAt()))
                .orElseThrow(() -> new ResourceNotFoundException("Report verification token", verificationToken));
    }

    private static ResultEntryDtos.AuthorizationDto toDto(OrderAuthorization a) {
        return new ResultEntryDtos.AuthorizationDto(
                a.getOrderId(),
                a.getPathologistName(),
                a.getLicenseNumber(),
                a.getDigitalSignatureHash(),
                a.getVerificationToken(),
                a.getAuthorizedBy(),
                a.getAuthorizedAt()
        );
    }

    /**
     * 64 bits of CSPRNG entropy: the token is a public lookup key, so it must not be guessable.
     */
    private static String randomToken() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
