package com.loopers.application.activity;

import com.loopers.domain.like.LikeChangedEvent;
import com.loopers.domain.order.OrderFailedEvent;
import com.loopers.domain.order.OrderPaidEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동(좋아요/주문/조회)에 대한 서버 레벨 로깅. 핵심 트랜잭션과 분리된 부가 로직이다.
 * 쓰기 트랜잭션이 있는 이벤트는 커밋 후에만 의미가 있어 AFTER_COMMIT, 읽기 경로인 조회는 즉시 발화하는 @EventListener 를 쓴다.
 */
@Slf4j
@Component
public class UserActivityEventHandler {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeChanged(LikeChangedEvent event) {
        log.info("[user-activity] LIKE {} - userId={}, productId={}, eventId={}, at={}",
                event.action(), event.userId(), event.productId(), event.eventId(), event.occurredAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("[user-activity] ORDER_PAID - userId={}, orderId={}, finalAmount={}, items={}, eventId={}, at={}",
                event.userId(), event.orderId(), event.finalAmount(), event.items(), event.eventId(), event.occurredAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderFailed(OrderFailedEvent event) {
        log.info("[user-activity] ORDER_FAILED - userId={}, orderId={}, eventId={}, at={}",
                event.userId(), event.orderId(), event.eventId(), event.occurredAt());
    }

    @Async
    @EventListener
    public void onProductViewed(ProductViewedEvent event) {
        log.info("[user-activity] PRODUCT_VIEWED - userId={}, productId={}, eventId={}, at={}",
                event.userId(), event.productId(), event.eventId(), event.occurredAt());
    }
}