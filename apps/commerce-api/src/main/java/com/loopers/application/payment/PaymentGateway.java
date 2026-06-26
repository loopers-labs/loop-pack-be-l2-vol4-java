package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGatewayResult;

public interface PaymentGateway {
    boolean isRequestAvailable();
    PaymentGatewayResult request(PaymentGatewayCommand command);
    PaymentGatewayResult getByOrder(String userLoginId, Long orderId);
}
