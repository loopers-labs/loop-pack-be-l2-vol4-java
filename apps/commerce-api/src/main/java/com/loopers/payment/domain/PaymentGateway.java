package com.loopers.payment.domain;

import com.loopers.common.domain.Money;

public interface PaymentGateway {
    void requestPayment(Long orderId, Money amount);
}
