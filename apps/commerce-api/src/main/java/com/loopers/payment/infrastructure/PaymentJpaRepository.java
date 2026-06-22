package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.PaymentModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
}
