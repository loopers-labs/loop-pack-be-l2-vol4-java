package com.loopers.tddstudy.application.coupon;

import com.loopers.tddstudy.messaging.CouponIssueRequested;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CouponIssueConsumer {

    private final CouponIssueService couponIssueService;

    public CouponIssueConsumer(CouponIssueService couponIssueService) {
        this.couponIssueService = couponIssueService;
    }

    // 관심사 분리: metrics(collector)와 다른 컨슈머 그룹
    @KafkaListener(topics = CouponIssueRequested.TOPIC, groupId = "coupon-issuer")
    public void consume(CouponIssueRequested event, Acknowledgment ack) {
        couponIssueService.issue(event.requestId(), event.couponId(), event.userId());
        ack.acknowledge();
    }
}
