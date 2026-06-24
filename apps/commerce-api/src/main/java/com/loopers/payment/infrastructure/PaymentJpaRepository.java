package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPgTransactionKey(String pgTransactionKey);

    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);
}
