package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
    Optional<PaymentModel> findFirstByOrderIdOrderByIdDesc(Long orderId);
    List<PaymentModel> findByStatusIn(List<PaymentStatus> statuses);
}
