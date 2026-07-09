package com.loopers.infrastructure.outbox;

import java.time.ZonedDateTime;

/**
 * order-events 토픽으로 나가는 메시지 계약(JSON). 결제 성공 1건은 <b>상품별로 분해</b>되어 이 메시지 여러 개로 발행된다
 * (각 메시지 key = productId → 상품 단위 파티션 직렬화 → product_metrics 단일 writer 유지). CatalogEventMessage 와
 * 마찬가지로 producer/consumer 가 클래스를 공유하지 않고 JSON 형태 자체를 계약으로 삼는다.
 *
 * @param eventId    전역 유일 키(소비자 멱등 기준). 상품별 라인아이템마다 별개다.
 * @param type       사실의 종류(판매 확정)
 * @param productId  집계 대상 상품(= 파티셔닝 키)
 * @param quantity   해당 상품의 판매 수량(판매량 누적 단위)
 * @param orderId    출처 주문(추적/디버깅용)
 * @param userId     구매자
 * @param occurredAt 결제 성공 확정 시각
 */
public record OrderEventMessage(
        String eventId,
        OrderEventType type,
        Long productId,
        int quantity,
        Long orderId,
        Long userId,
        ZonedDateTime occurredAt
) {
}
