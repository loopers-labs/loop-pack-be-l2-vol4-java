package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * coupon-issue-requests를 소비해 선착순 발급을 처리하는 컨슈머.
 * <p>
 * 파티션 키가 couponId라, 같은 쿠폰의 요청은 한 파티션 = 한 스레드에서 순서대로 처리된다(직렬화).
 * 수량 초과는 CouponIssueService의 조건부 UPDATE가 최종 방어한다. 배치 처리 후 수동 ack.
 */
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private static final String GROUP_ID = "coupon-issue";

    private final CouponIssueService couponIssueService;

    @KafkaListener(
        topics = Topics.COUPON_ISSUE_REQUESTS,
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onCouponIssueRequests(List<CouponIssueRequestMessage> messages, Acknowledgment acknowledgment) {
        for (CouponIssueRequestMessage message : messages) {
            couponIssueService.issue(message.requestId(), message.couponId(), message.userId());
        }
        acknowledgment.acknowledge();
    }
}
