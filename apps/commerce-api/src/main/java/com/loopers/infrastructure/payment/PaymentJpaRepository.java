package com.loopers.infrastructure.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.payment.PaymentModel;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    boolean existsByOrderId(Long orderId);
}
