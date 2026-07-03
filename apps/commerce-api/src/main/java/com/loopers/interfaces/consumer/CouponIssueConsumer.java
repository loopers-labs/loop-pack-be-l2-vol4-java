package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.config.CouponIssueKafkaListenerConfig;
import com.loopers.confg.kafka.message.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes Redis-approved coupon issue events inside commerce-api.
 *
 * <p>Redis Lua has already decided duplicate/sold-out/success before an event reaches this listener.
 * Kafka is used as a write-behind buffer for successful issues, so producer keys are distributed by userId
 * instead of couponId.
 *
 * <p>The listener processes records in chunks and commits offsets manually after DB persistence succeeds.
 * Terminal request state is the idempotency guard for redelivery.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private static final int CHUNK_SIZE = 500;

    private final CouponIssueProcessor couponIssueProcessor;

    @KafkaListener(
        topics = "${commerce-events.topics.coupon-issue}",
        groupId = "${commerce-events.consumer-groups.coupon-issue:commerce-api-coupon-issue}",
        containerFactory = CouponIssueKafkaListenerConfig.COUPON_ISSUE_BATCH_LISTENER
    )
    public void onCouponIssueRequested(List<EventEnvelope> messages, Acknowledgment acknowledgment) {
        List<String> requestIds = new ArrayList<>(messages.size());
        for (EventEnvelope envelope : messages) {
            Object requestId = envelope.payload() == null ? null : envelope.payload().get("requestId");
            if (requestId == null) {
                log.warn("[CouponIssue] message without requestId skipped. eventId={}", envelope.eventId());
                continue;
            }
            requestIds.add(requestId.toString());
        }

        for (int from = 0; from < requestIds.size(); from += CHUNK_SIZE) {
            couponIssueProcessor.processBatch(
                requestIds.subList(from, Math.min(from + CHUNK_SIZE, requestIds.size())));
        }
        acknowledgment.acknowledge();
    }
}
