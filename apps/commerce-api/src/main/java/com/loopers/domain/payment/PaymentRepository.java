package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    PaymentModel getById(Long paymentId);

    PaymentModel getByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<PaymentModel> findPendingRequestedBefore(ZonedDateTime threshold);

    int confirmIfUnresolved(Long paymentId, PaymentStatus status, String reason);
}
