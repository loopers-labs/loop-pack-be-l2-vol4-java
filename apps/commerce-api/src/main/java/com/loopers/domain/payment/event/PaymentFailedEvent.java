package com.loopers.domain.payment.event;

import com.loopers.domain.payment.PaymentFailureCategory;

/**
 * 결제 실패 확정 이벤트. AFTER_COMMIT 단계에서 발행된다.
 *
 *  - category 가 RECOVERABLE 이면 OrderEventHandler 가 Order 와 재고를 유지한다 (사용자 재시도 가능).
 *  - category 가 TERMINAL 이면 Order FAILED + 재고 복구로 마무리한다.
 */
public record PaymentFailedEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    String reason,
    PaymentFailureCategory category
) {
}
