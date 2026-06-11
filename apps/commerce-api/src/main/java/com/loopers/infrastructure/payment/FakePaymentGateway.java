package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 외부 PG 연동이 준비되기 전까지 사용하는 가짜 구현체.
 *
 * <p>실제 흐름에서 paymentKey 는 결제창 인증 완료 시 PG 가 발급하지만,
 * 백엔드 과제에서는 인증 단계(프론트-PG 구간)를 생략하고 프론트가 유효한 paymentKey 를
 * 들고 confirm 을 호출한다고 가정한다. 승인은 항상 성공을 반환한다.
 *
 * <p>운영 환경에서는 토스페이먼츠 승인 API 같은 실제 HTTP 호출 구현체로 교체된다.
 */
@Component
@Profile("!prod")
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult confirm(String paymentKey, Long orderId, Long amount) {
        String fakeTransactionId = "FAKE-" + UUID.randomUUID();
        return PaymentResult.success(fakeTransactionId);
    }

    @Override
    public Optional<PaymentResult> inquire(Long orderId) {
        // 가짜 PG 에는 조회할 결제 기록이 없다 — 항상 "결제 안 됨" 으로 응답
        return Optional.empty();
    }
}
