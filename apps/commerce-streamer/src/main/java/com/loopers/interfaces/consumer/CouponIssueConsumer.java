package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssuer;
import com.loopers.application.metrics.EventEnvelope;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 선착순 쿠폰 발급 요청(coupon-issue-requests, COUPON_ISSUE_REQUESTED)을 배치로 받아 발급을 확정한다 (Slice 4, Step3).
 *
 * <p>metrics 컨슈머와 <b>다른 컨슈머 그룹</b>({@link CouponIssuer#CONSUMER_GROUP})으로 독립 소비한다. 수동 커밋
 * (at-least-once): 발급 트랜잭션({@link CouponIssuer#apply})이 커밋된 뒤에만 ack 한다. 재전달돼도 event_handled 멱등으로
 * 이중 발급이 차단된다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponIssuer couponIssuer;

    @KafkaListener(
            topics = "${event-topics.coupon-issue}",
            groupId = CouponIssuer.CONSUMER_GROUP,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<EventEnvelope> envelopes, Acknowledgment acknowledgment) {
        couponIssuer.apply(envelopes);
        acknowledgment.acknowledge();
    }
}
