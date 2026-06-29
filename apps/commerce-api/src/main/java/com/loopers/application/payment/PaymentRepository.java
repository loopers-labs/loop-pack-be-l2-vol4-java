package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    boolean existsByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses);
    List<PaymentModel> findAllByStatusAndCreatedAtBefore(PaymentStatus status, ZonedDateTime time);
}
