package com.loopers.application.coupon;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.coupon.CouponIssueRequestedEvent;
import com.loopers.infrastructure.outbox.EventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link CouponIssueRequestedEvent}를 요청 접수 트랜잭션 <b>커밋 직전(BEFORE_COMMIT)</b>에 outbox로 적재한다
 * (Slice 4, Step3). 요청 INSERT와 outbox INSERT를 같은 트랜잭션으로 묶어 원자화 — 접수가 롤백되면 이벤트도 사라진다.
 * 실제 Kafka 전송은 커밋 이후 하이브리드 발행(즉시발행 + 릴레이)이 담당한다.
 *
 * <p>{@code key=couponId}: 같은 쿠폰 요청을 같은 파티션으로 직렬화해 컨슈머의 원자 UPDATE 경합 범위를 파티션 단위로 좁힌다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueRequestEventOutboxListener {

    private final OutboxAppender outboxAppender;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(CouponIssueRequestedEvent event) {
        outboxAppender.append(
                "coupon",
                event.couponId(),
                "COUPON_ISSUE_REQUESTED",
                EventTopics.COUPON_ISSUE_REQUESTS,
                String.valueOf(event.couponId()),
                event,
                0L
        );
    }
}
