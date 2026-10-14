package com.loopers.application.eventpublish;

import com.loopers.domain.event.LikeChangedEvent;
import com.loopers.domain.event.OrderCompletedEvent;
import com.loopers.domain.event.PaymentSettledEvent;
import com.loopers.domain.event.ProductViewedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동 로깅 — 주요 로직과 완전히 분리된 부가 로직.
 * <p>
 * AFTER_COMMIT 로 붙여 도메인 트랜잭션이 롤백된 경우 로그가 남지 않도록 하고,
 * @Async 로 응답 지연에 포함되지 않도록 한다.
 * <p>
 * 로그 자체가 실패해도 도메인은 이미 커밋됐으므로 사용자 응답은 그대로 성공한다.
 */
@Component
public class UserActivityLogger {

    private static final Logger activity = LoggerFactory.getLogger("USER_ACTIVITY");

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logLike(LikeChangedEvent event) {
        activity.info("ACTION=LIKE_CHANGED user={} product={} action={} at={}",
            event.userId(), event.productId(), event.action(), event.occurredAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logView(ProductViewedEvent event) {
        activity.info("ACTION=PRODUCT_VIEWED user={} product={} at={}",
            event.userId(), event.productId(), event.occurredAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logOrder(OrderCompletedEvent event) {
        activity.info("ACTION=ORDER_COMPLETED user={} order={} amount={} lines={}",
            event.userId(), event.orderId(), event.finalPrice(), event.lines().size());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logPayment(PaymentSettledEvent event) {
        activity.info("ACTION=PAYMENT_SETTLED user={} payment={} order={} outcome={}",
            event.userId(), event.paymentId(), event.orderId(), event.outcome());
    }
}
