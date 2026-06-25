package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderNumberAndStatusInOrderByIdDesc(String orderNumber, Collection<PaymentStatus> statuses);

    Optional<Payment> findByTransactionKey(String transactionKey);
}
