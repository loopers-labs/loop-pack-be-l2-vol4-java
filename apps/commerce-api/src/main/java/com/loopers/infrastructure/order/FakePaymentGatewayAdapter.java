package com.loopers.infrastructure.order;

import com.loopers.domain.order.PaymentCommand;
import com.loopers.domain.order.PaymentGateway;
import com.loopers.domain.order.PaymentResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FakePaymentGatewayAdapter implements PaymentGateway {

    @Override
    public PaymentResult requestPayment(PaymentCommand command) {
        return PaymentResult.success(UUID.randomUUID().toString());
    }
}