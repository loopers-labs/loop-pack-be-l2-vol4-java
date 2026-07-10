package com.loopers.application.coupon;

import com.loopers.domain.outbox.OutboxEventHandler;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.infrastructure.kafka.KafkaOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 릴레이(③)가 COUPON_ISSUE_REQUESTED outbox를 위임받아 coupon-issue-requests 토픽으로 발행한다.
@RequiredArgsConstructor
@Component
public class CouponIssueKafkaOutboxEventHandler implements OutboxEventHandler {

    private static final String TOPIC = "coupon-issue-requests";
    private static final String SUPPORTED_EVENT_TYPE = "COUPON_ISSUE_REQUESTED";

    private final KafkaOutboxPublisher kafkaOutboxPublisher;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED_EVENT_TYPE.equals(eventType);
    }

    @Override
    public void handle(OutboxModel outbox) {
        kafkaOutboxPublisher.publishAndMarkDone(outbox, TOPIC);
    }
}
