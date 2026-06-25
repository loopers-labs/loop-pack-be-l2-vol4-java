package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderIdAndStatusInOrderByIdDesc(Long orderId, Collection<PaymentStatus> statuses);

    Optional<Payment> findByTransactionKey(String transactionKey);
}
