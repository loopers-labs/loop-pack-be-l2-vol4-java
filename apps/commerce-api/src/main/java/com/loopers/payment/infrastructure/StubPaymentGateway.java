package com.loopers.payment.infrastructure;

import com.loopers.common.domain.Money;
import com.loopers.payment.domain.PaymentGateway;
import org.springframework.stereotype.Component;

@Component
public class StubPaymentGateway implements PaymentGateway {

    @Override
    public void requestPayment(Long orderId, Money amount) {
    }
}
