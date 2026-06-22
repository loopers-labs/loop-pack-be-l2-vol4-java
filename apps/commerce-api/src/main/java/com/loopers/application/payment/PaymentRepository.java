package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
}
