package com.lims.diagnostics.repository;

import com.lims.diagnostics.entity.OrderAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderAuthorizationRepository extends JpaRepository<OrderAuthorization, UUID> {
    Optional<OrderAuthorization> findByOrderId(UUID orderId);
    Optional<OrderAuthorization> findByVerificationToken(String verificationToken);
}
