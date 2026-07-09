package com.loopers.domain.order.event;

import java.time.ZonedDateTime;

/**
 * 주문이 생성된 사실(이미 커밋됨)을 알리는 도메인 이벤트.
 *
 * <p>주문 생성 자체는 주요 로직이고, 행동 로깅 같은 부가 로직만 이벤트로 분리한다.
 * (판매량 집계는 "주문"이 아니라 "결제 성공" 기준이므로 {@code PaymentCompletedEvent} 가 담당한다.)</p>
 */
public record OrderPlacedEvent(Long orderId, Long userId, ZonedDateTime occurredAt) {
}
