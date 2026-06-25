package com.loopers.infrastructure.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {
    Optional<PaymentJpaEntity> findByIdAndUserLoginId(Long id, String userLoginId);
    Optional<PaymentJpaEntity> findByOrderIdAndUserLoginId(Long orderId, String userLoginId);
    Optional<PaymentJpaEntity> findByOrderId(Long orderId);
}
