package com.loopers.interfaces.consumer.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueRequestEventService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.kafka.event.EventMessage;
import com.loopers.support.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "commerce.consumers.coupon-issue.enabled", havingValue = "true")
public class CouponIssueRequestConsumer {

    private final CouponIssueRequestEventService couponIssueRequestEventService;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    @KafkaListener(
        topics = {"${loopers.kafka.topics.coupon-issue-requests:coupon-issue-requests}"},
        groupId = "${loopers.kafka.consumer-groups.coupon-issue:coupon-issue-consumer}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        try {
            for (ConsumerRecord<Object, Object> message : messages) {
                couponIssueRequestEventService.process(toEventMessage(message.value()));
            }
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            eventMetrics.recordKafkaConsumerFailure(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, "UNKNOWN");
            throw e;
        }
    }

    private EventMessage toEventMessage(Object value) {
        if (value instanceof EventMessage eventMessage) {
            return eventMessage;
        }
        if (value instanceof byte[] bytes) {
            return deserialize(new String(bytes, StandardCharsets.UTF_8));
        }
        if (value instanceof String text) {
            return deserialize(text);
        }

        eventMetrics.recordKafkaConsumerFailure(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, "UNKNOWN");
        throw new IllegalArgumentException("지원하지 않는 Kafka 메시지 형식입니다.");
    }

    private EventMessage deserialize(String text) {
        try {
            return objectMapper.readValue(text, EventMessage.class);
        } catch (JsonProcessingException e) {
            eventMetrics.recordKafkaConsumerFailure(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, "UNKNOWN");
            throw new IllegalArgumentException("Kafka 메시지 해석에 실패했습니다.", e);
        }
    }
}
