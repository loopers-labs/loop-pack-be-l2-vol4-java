package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);
}
