package com.loopers.tddstudy.domain.order;

public interface PaymentGateway {

    // "SUCCESS", "FAIL", "TIMEOUT" 반환
    String requestPayment(Long orderId, int amount);
}
