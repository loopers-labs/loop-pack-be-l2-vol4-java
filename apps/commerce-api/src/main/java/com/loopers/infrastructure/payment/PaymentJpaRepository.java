package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByOrderIdAndDeletedAtIsNull(Long orderId);

    Optional<PaymentModel> findByTransactionKeyAndDeletedAtIsNull(String transactionKey);

    List<PaymentModel> findAllByStatusAndDeletedAtIsNull(PaymentStatus status);

    List<PaymentModel> findAllByStatusAndTransactionKeyIsNullAndCreatedAtBeforeAndDeletedAtIsNull(PaymentStatus status, ZonedDateTime cutoff);
}
