package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.order.PaymentGateway;

public class FakePaymentGateway implements PaymentGateway {

    private String result = "SUCCESS";

    @Override
    public String requestPayment(Long orderId, int amount) {
        return result;
    }

    // 테스트에서 결과 제어
    public void willReturn(String result) {
        this.result = result;
    }
}
