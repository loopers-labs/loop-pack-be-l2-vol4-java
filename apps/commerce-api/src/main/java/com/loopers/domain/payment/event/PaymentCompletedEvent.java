package com.loopers.domain.payment.event;

import java.time.ZonedDateTime;

/**
 * 결제가 성공으로 확정된 사실(이미 커밋됨)을 알리는 도메인 이벤트.
 *
 * <p>결제 성공 확정은 콜백/정산이 수렴하는 {@code PaymentResultApplier#apply} 한 곳에서만 일어나므로
 * 발행 지점도 그곳 하나다. 알림(@Async)·행동 로깅 같은 부가 로직, 그리고 판매량 전파(Step 2)가 이 이벤트를 듣는다.</p>
 */
public record PaymentCompletedEvent(Long orderId, Long userId, Long amount, ZonedDateTime occurredAt) {
}
