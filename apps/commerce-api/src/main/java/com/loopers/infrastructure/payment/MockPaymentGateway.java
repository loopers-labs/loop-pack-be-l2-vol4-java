package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
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
        // 紐⑦궧 ?섍꼍?대?濡?痍⑥냼 ??蹂꾨룄???몃? ?곕룞 ?놁씠 濡쒓렇 異쒕젰 ?섏??쇰줈 媛꾨왂??
    }
}
