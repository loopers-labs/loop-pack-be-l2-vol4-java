package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.KafkaConsumerConfig;
import com.loopers.domain.coupon.CouponIssueProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * coupon-issue-requests 를 소비해 선착순 발급을 처리한다. key=couponId 라 한 쿠폰 요청은 단일 파티션에서 순차 처리된다.
 * 발급 처리(멱등 포함)가 끝난 뒤에만 manual ack. 예외 시 미ack → 재전달, 멱등이 중복 반영을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private static final String TYPE_COUPON_ISSUE_REQUESTED = "COUPON_ISSUE_REQUESTED";

    private final CouponIssueProcessor couponIssueProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "coupon-issue-requests",
            groupId = "coupon-issue",
            containerFactory = KafkaConsumerConfig.METRICS_LISTENER
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record.value());
        acknowledgment.acknowledge();
    }

    void handle(String message) {
        EventEnvelope envelope = parse(message);
        if (!TYPE_COUPON_ISSUE_REQUESTED.equals(envelope.eventType())) {
            log.warn("알 수 없는 이벤트 타입 - eventType={}, eventId={}", envelope.eventType(), envelope.eventId());
            return;
        }
        JsonNode payload = envelope.payload();
        couponIssueProcessor.process(
                envelope.eventId(),
                payload.get("requestId").asText(),
                payload.get("couponId").asLong(),
                payload.get("userId").asLong()
        );
    }

    private EventEnvelope parse(String message) {
        try {
            return objectMapper.readValue(message, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 역직렬화 실패: " + message, e);
        }
    }
}