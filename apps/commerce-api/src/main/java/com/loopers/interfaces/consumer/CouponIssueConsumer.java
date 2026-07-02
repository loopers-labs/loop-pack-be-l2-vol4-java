package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueApplicationService;
import com.loopers.application.coupon.CouponIssueMessage;
import com.loopers.interfaces.api.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * coupon-issue-requests 를 소비해 선착순 쿠폰을 발급하는 consumer(commerce-api 호스팅 — 도메인 불변식 소유).
 *
 * <p>record 리스너로 요청을 한 건씩 받아 {@link CouponIssueApplicationService} 가 요청 단위 트랜잭션 + requestId
 * 멱등으로 발급을 확정한다. key=templateId 로 발행돼 같은 템플릿 요청은 한 파티션 → 단일 스레드가 순차
 * 처리하므로, 락 없이 {@code issuedCount < issueLimit} 가 강제된다(파티션 직렬화).</p>
 *
 * <p><b>manual ack</b>: 발급 트랜잭션이 커밋된(=handle 정상 반환) 뒤에만 {@code ack.acknowledge()} 로 오프셋을
 * 커밋한다. handle 이 예외를 던지면 acknowledge 에 도달하지 못하고, {@link KafkaConsumerConfig} 의 에러 핸들러가
 * 재시도 후 {@code coupon-issue-requests.DLT} 로 격리한다(성공했을 때만 오프셋 전진 → at-least-once).</p>
 *
 * <p>offset reset=earliest — 접수된 요청을 유실 없이 모두 처리해야 하기 때문.</p>
 */
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponIssueApplicationService couponIssueApplicationService;

    @KafkaListener(
            topics = KafkaTopicConfig.COUPON_ISSUE_REQUESTS,
            groupId = "coupon-issuer",
            containerFactory = KafkaConsumerConfig.RECORD_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consume(CouponIssueMessage message, Acknowledgment ack) {
        couponIssueApplicationService.handle(message);
        ack.acknowledge();
    }
}
