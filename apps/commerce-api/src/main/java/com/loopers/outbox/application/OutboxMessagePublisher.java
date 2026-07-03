package com.loopers.outbox.application;

/**
 * Outbox 에 적재된 메시지를 브로커로 발행하는 포트.
 * payload 는 직렬화된 JSON 문자열이며, 구현체는 이를 그대로 전송한다(이벤트 타입에 무관).
 */
public interface OutboxMessagePublisher {

    void publish(String topic, String key, String payload);
}
