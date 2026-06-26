package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public final class PaymentCriteria {

    private PaymentCriteria() {
    }

    /**
     * 결제 요청. {@code cardNo}(풀 PAN)는 PG 호출에만 쓰이는 transient 값으로, 엔티티에 저장되지 않는다.
     */
    public record Pay(Long userId, Long orderId, CardType cardType, String cardNo) {
    }

    /**
     * PG 콜백 수신. transactionKey 로 결제를 식별하고 결과 상태를 확정한다.
     * {@code amount} 는 통지된 결제 금액으로, 성공 확정 전 우리 보관 금액과 교차검증한다(돈 사고 방지).
     */
    public record Callback(String transactionKey, PaymentStatus status, Long amount, String reason) {
    }
}
