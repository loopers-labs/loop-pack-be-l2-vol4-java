package com.loopers.domain.payment.event;

import com.loopers.domain.shared.Money;

/**
 * 결제 성공 확정 이벤트. AFTER_COMMIT 단계에서 발행되어
 * Order 도메인의 상태 전이(PAID) 와 후속 작업(알림 등) 을 트리거한다.
 */
public record PaymentSucceededEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    Money amount
) {
}
