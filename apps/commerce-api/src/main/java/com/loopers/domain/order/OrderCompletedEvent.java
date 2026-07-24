package com.loopers.domain.order;

import java.util.List;

/**
 * "주문이 완료되었다"는 사실을 나타내는 도메인 이벤트.
 * <p>
 * {@code OrderFacade.createOrder} 트랜잭션이 커밋된 뒤 발행된다. 주문 완료 알림, 유저 행동 로깅,
 * 그리고 상품별 판매량 집계(Kafka 파이프라인)가 이 사실을 각자 구독한다.
 * <p>
 * 상품별 판매량 집계를 위해 주문에 포함된 상품 라인({@code productId, quantity})을 함께 담는다.
 */
public record OrderCompletedEvent(Long orderId, Long userId, Long finalPrice, List<Line> lines) {

    public record Line(Long productId, int quantity) {
    }
}
