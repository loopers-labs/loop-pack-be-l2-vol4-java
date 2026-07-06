package com.loopers.confg.kafka.message;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka 메시지 공용 봉투 — 발행측(commerce-api)과 소비측(commerce-streamer)이 공유하는 메시지 계약.
 *
 * <p>모든 도메인 이벤트는 이 봉투에 담겨 발행된다.
 * <ul>
 *   <li>{@code eventId} — 이벤트 고유 식별자(UUID). 소비측 멱등 처리({@code event_handled})의 키.</li>
 *   <li>{@code eventType} — 이벤트 종류 (예: LIKE_CHANGED, PAYMENT_COMPLETED, PRODUCT_VIEWED).</li>
 *   <li>{@code occurredAt} — 발생 시각(ISO-8601 문자열). 소비측이 최신 이벤트 판정에 사용할 수 있다.</li>
 *   <li>{@code payload} — 이벤트별 데이터. 소비자가 발행자를 되조회하지 않도록 필요한 상태를 담는다
 *       (Event-Carried State Transfer).</li>
 * </ul>
 */
public record EventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    Map<String, Object> payload
) {

    public static EventEnvelope of(String eventType, Map<String, Object> payload) {
        return of(UUID.randomUUID().toString(), eventType, payload);
    }

    /** 호출자가 식별자를 이미 갖고 있을 때(예: 쿠폰 발급 requestId = eventId) 사용. */
    public static EventEnvelope of(String eventId, String eventType, Map<String, Object> payload) {
        return new EventEnvelope(
            eventId,
            eventType,
            ZonedDateTime.now().toOffsetDateTime().toString(),
            payload
        );
    }
}
