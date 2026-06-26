package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayStatus;
import com.loopers.domain.payment.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// @Component // 실제 연동(HttpPaymentGateway) 테스트를 위해 주석 처리
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentGatewayResult requestPayment(Long orderId, BigDecimal amount, PaymentMethod method) {
        return new PaymentGatewayResult(
                UUID.randomUUID().toString(),
                LocalDateTime.now()
        );
    }

    @Override
    public void cancelPayment(String transactionId, BigDecimal amount) {
        // 모킹 환경이므로 취소 시 별도의 외부 연동 없이 로그 출력 수준으로 간략화
    }

    @Override
    public PaymentGatewayQueryResult queryPaymentStatus(Long orderId) {
        return new PaymentGatewayQueryResult(
                PaymentGatewayStatus.APPROVED,
                UUID.randomUUID().toString(),
                LocalDateTime.now()
        );
    }
}
