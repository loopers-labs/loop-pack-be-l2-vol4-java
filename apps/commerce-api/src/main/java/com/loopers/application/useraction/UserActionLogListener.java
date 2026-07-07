package com.loopers.application.useraction;

import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.domain.product.event.ProductViewed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActionLogListener {

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeAdded(LikeAdded e) {
        log.info("[UserAction] LIKE_ADDED userId={} productId={}", e.userId(), e.productId());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeRemoved(LikeRemoved e) {
        log.info("[UserAction] LIKE_REMOVED userId={} productId={}", e.userId(), e.productId());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced e) {
        log.info("[UserAction] ORDER_PLACED userId={} orderId={} finalAmount={}",
            e.userId(), e.orderId(), e.finalAmount());
    }

    // 조회는 읽기 경로(트랜잭션 없음) → AFTER_COMMIT 아닌 일반 @EventListener
    @Async("eventExecutor")
    @EventListener
    public void onProductViewed(ProductViewed e) {
        log.info("[UserAction] PRODUCT_VIEWED userId={} productId={}", e.userId(), e.productId());
    }
}
