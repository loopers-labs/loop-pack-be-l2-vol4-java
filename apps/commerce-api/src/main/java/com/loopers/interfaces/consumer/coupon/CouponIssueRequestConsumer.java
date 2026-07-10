package com.loopers.interfaces.consumer.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.infrastructure.kafka.KafkaEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// commerce-api가 발행한 coupon-issue-requests를 commerce-api 스스로 소비한다 - 쿠폰 도메인·테이블이
// 전부 commerce-api에 있어, Kafka를 시스템 간 전파가 아니라 발급 처리를 비동기 큐로 흘려보내는 용도로 쓴다.
// 단건 처리, 기본 컨테이너 팩토리(spring.kafka.listener.ack-mode: manual가 전역 적용됨) 사용.
// consumer.value-deserializer가 ByteArrayDeserializer이므로 레코드 값은 byte[]다.
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueRequestConsumer {

    private static final String SUPPORTED_EVENT_TYPE = "COUPON_ISSUE_REQUESTED";

    private final CouponIssueProcessor couponIssueProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(id = "couponIssueRequestConsumer", topics = "coupon-issue-requests", groupId = "coupon-issue-consumer")
    public void listen(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        try {
            KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
            if (SUPPORTED_EVENT_TYPE.equals(envelope.eventType())) {
                String requestId = envelope.payload().get("requestId").asText();
                Long couponTemplateId = envelope.payload().get("couponTemplateId").asLong();
                Long userId = envelope.payload().get("userId").asLong();
                couponIssueProcessor.process(envelope.eventId(), requestId, couponTemplateId, userId);
            } else {
                log.warn("처리할 수 없는 이벤트 타입입니다. eventType={}", envelope.eventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("coupon-issue-requests 처리 실패. key={}", record.key(), e);
        }
    }
}
