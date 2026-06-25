package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
}
