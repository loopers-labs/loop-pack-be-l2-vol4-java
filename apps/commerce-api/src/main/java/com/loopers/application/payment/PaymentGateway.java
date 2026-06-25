package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGatewayResult;

public interface PaymentGateway {
    PaymentGatewayResult request(PaymentGatewayCommand command);
    PaymentGatewayResult getByOrder(String userLoginId, Long orderId);
}
