package com.loopers.infrastructure.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.payment.PaymentModel;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
