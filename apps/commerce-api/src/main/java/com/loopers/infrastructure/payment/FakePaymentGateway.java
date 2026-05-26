package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 외부 PG 연동이 준비되기 전까지 사용하는 가짜 구현체.
 * 항상 성공 응답을 반환한다.
 *
 * 운영 환경에서는 PgPaymentGatewayClient 같은 실제 HTTP 호출 구현체로 교체된다.
 */
@Component
@Profile("!prod")
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult request(Long orderId, Long amount) {
        String fakeTransactionId = "FAKE-" + UUID.randomUUID();
        return PaymentResult.success(fakeTransactionId);
    }
}
