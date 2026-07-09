package com.loopers.infrastructure.outbox;

import java.time.ZonedDateTime;

/**
 * catalog-events 토픽으로 나가는 메시지 계약(JSON). producer(commerce-api)와 consumer(commerce-streamer)는
 * 별도 모듈이라 이 클래스를 공유하지 않는다 — <b>JSON 형태 자체가 계약</b>이며 소비자는 동일 형태의 사본을 갖는다.
 * (서비스 간 공유 jar 결합을 피하는 이벤트 기반 시스템의 통상 관행.)
 *
 * @param eventId    전역 유일 키(소비자 멱등 기준)
 * @param type       사실의 종류(좋아요/취소/조회)
 * @param productId  집계 대상 상품(= 파티셔닝 키)
 * @param userId     행위 주체(조회는 비로그인일 수 있어 nullable)
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
