package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 시스템 간 전파되는 모든 Kafka 메시지의 공통 envelope(봉투) 계약.
 *
 * <p>토픽 하나가 여러 {@code eventType}을 실어 나르므로(예: catalog-events = LIKE_CHANGED + PRODUCT_VIEWED),
 * 소비자는 {@code eventType}으로 분기하고 {@code payload}를 해당 타입으로 해석한다. {@code payload}는
 * 임의 구조를 그대로 싣기 위해 {@link JsonNode}로 둔다(이중 인코딩 방지 — outbox에 저장된 payload JSON을
 * 릴레이가 트리로 파싱해 끼워 넣는다).
 *
 * <p>{@code eventId}는 outbox PK(전역 고유) = 소비자 멱등 키. {@code version}은 같은 집계 대상에 대한
 * 최신성 비교용(stale 이벤트가 최신 집계를 덮어쓰지 않게 한다).
 *
 * @param eventId       전역 고유 이벤트 식별자(outbox PK). 소비자 멱등 키.
 * @param eventType     이벤트 종류(LIKE_CHANGED / PRODUCT_VIEWED / ORDER_PAID / COUPON_ISSUE_REQUESTED ...)
 * @param aggregateType 집계 루트 종류(like / product / order / coupon)
 * @param aggregateId   집계 루트 식별자(productId / orderId / couponId 등)
 * @param version       최신성 비교 기준(클수록 최신)
 * @param occurredAt    이벤트 발생 시각(ISO-8601 문자열)
 * @param payload       이벤트 본문(타입별 구조)
 */
public record EventEnvelope(
        Long eventId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        long version,
        String occurredAt,
        JsonNode payload
) {
}
