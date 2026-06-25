package com.loopers.tddstudy.infrastructure.order;

import com.loopers.tddstudy.domain.order.PaymentGateway;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;


public class StubPaymentGateway implements PaymentGateway {

    @Override
    public String requestPayment(Long orderId, int amount) {
        // 실제 PG 연동 전 항상 성공 반환
        return "SUCCESS";
    }
}
