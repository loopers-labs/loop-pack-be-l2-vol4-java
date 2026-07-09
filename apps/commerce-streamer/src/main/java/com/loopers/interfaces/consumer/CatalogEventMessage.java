package com.loopers.interfaces.consumer;

import java.time.ZonedDateTime;

/**
 * catalog-events 토픽에서 들어오는 메시지 계약(JSON). producer(commerce-api)의 동명 record 와 <b>JSON 형태가 계약</b>이며,
 * 공유 jar 결합을 피하기 위해 소비자는 동일 형태의 사본을 갖는다(이벤트 기반 시스템의 통상 관행).
 *
 * @param eventId    전역 유일 키(소비자 멱등 = event_handled 의 기준)
 * @param type       사실의 종류(좋아요/취소)
 * @param productId  집계 대상 상품(= 파티셔닝 키)
 * @param userId     행위 주체(nullable)
 * @param occurredAt 사실 발생 시각
 */
public record CatalogEventMessage(
        String eventId,
        CatalogEventType type,
        Long productId,
        Long userId,
        ZonedDateTime occurredAt
) {
}
