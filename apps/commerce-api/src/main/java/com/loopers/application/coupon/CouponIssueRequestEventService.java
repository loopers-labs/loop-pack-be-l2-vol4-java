package com.loopers.application.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.kafka.event.CouponIssueRequestEventPayload;
import com.loopers.kafka.event.EventMessage;
import com.loopers.support.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class CouponIssueRequestEventService {

    private final CouponCommandService couponCommandService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    @Transactional
    public ProcessResult process(EventMessage message) {
        validateMessage(message);
        if (eventHandledRepository.exists(message.eventId())) {
            eventMetrics.recordKafkaConsumerDuplicate(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, message.eventType());
            return ProcessResult.DUPLICATE;
        }

        eventHandledRepository.save(new EventHandled(
            message.eventId(),
            EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS,
            message.eventType(),
            message.aggregateType(),
            message.aggregateId(),
            ZonedDateTime.now()
        ));

        if (!EventOutbox.EVENT_COUPON_ISSUE_REQUESTED.equals(message.eventType())) {
            eventMetrics.recordKafkaConsumerSuccess(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, message.eventType());
            return ProcessResult.IGNORED;
        }

        CouponIssueRequestEventPayload payload = deserializePayload(message);
        CouponResult.IssueRequest issueRequest = couponCommandService.processIssueRequest(payload.requestId());
        eventMetrics.recordKafkaConsumerSuccess(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, message.eventType());

        return switch (issueRequest.status()) {
            case SUCCEEDED -> ProcessResult.ISSUED;
            case FAILED -> ProcessResult.REJECTED;
            case PENDING -> ProcessResult.PENDING;
        };
    }

    private CouponIssueRequestEventPayload deserializePayload(EventMessage message) {
        try {
            return objectMapper.readValue(message.payload(), CouponIssueRequestEventPayload.class);
        } catch (JsonProcessingException e) {
            eventMetrics.recordKafkaConsumerFailure(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, message.eventType());
            throw new IllegalArgumentException("쿠폰 발급 요청 이벤트 payload 해석에 실패했습니다.", e);
        }
    }

    private void validateMessage(EventMessage message) {
        if (message == null || isBlank(message.eventId()) || isBlank(message.eventType())) {
            eventMetrics.recordKafkaConsumerFailure(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, "UNKNOWN");
            throw new IllegalArgumentException("이벤트 메시지는 필수입니다.");
        }
        if (isBlank(message.aggregateType()) || isBlank(message.aggregateId()) || isBlank(message.payload())) {
            eventMetrics.recordKafkaConsumerFailure(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS, message.eventType());
            throw new IllegalArgumentException("이벤트 메시지의 aggregate와 payload는 필수입니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum ProcessResult {
        ISSUED,
        REJECTED,
        PENDING,
        DUPLICATE,
        IGNORED
    }
}
