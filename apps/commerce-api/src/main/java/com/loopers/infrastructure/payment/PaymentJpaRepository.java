package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {
    Optional<PaymentModel> findByOrderId(Long orderId);
    java.util.List<PaymentModel> findAllByStatusAndCreatedAtBefore(com.loopers.domain.payment.PaymentStatus status, java.time.ZonedDateTime time);
}
