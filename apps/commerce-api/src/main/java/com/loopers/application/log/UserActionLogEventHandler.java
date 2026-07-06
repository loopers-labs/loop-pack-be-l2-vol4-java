package com.loopers.application.log;

import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.payment.PaymentFailedEvent;
import com.loopers.domain.payment.PaymentSucceededEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동(조회·좋아요·주문·결제)에 대한 서버 레벨 로깅 — 핵심 거래에 반응만 하는 부가 로직.
 * DB 를 쓰지 않으므로 REQUIRES_NEW 는 불필요하고, 로깅 실패가 거래에 영향을 주지 않도록 @Async 로 분리한다.
 * 트랜잭션이 있는 행동은 AFTER_COMMIT 으로 "커밋된 사실만" 기록한다 (롤백된 주문이 로그에 남지 않도록).
 */
@Slf4j
@Component
public class UserActionLogEventHandler {

    // 조회 경로에는 트랜잭션이 없어 @TransactionalEventListener 가 동작하지 않는다 — 일반 @EventListener 를 쓴다.
    @Async
    @EventListener
    public void onProductViewed(ProductViewedEvent event) {
        log.info("USER_ACTION action=PRODUCT_VIEW productId={}", event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductLiked(ProductLikedEvent event) {
        log.info("USER_ACTION action=PRODUCT_LIKE userId={} productId={}", event.userId(), event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUnliked(ProductUnlikedEvent event) {
        log.info("USER_ACTION action=PRODUCT_UNLIKE userId={} productId={}", event.userId(), event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("USER_ACTION action=ORDER_CREATE userId={} orderId={} paymentAmount={}",
            event.userId(), event.orderId(), event.paymentAmount().getAmount());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("USER_ACTION action=PAYMENT_SUCCESS userId={} orderId={} amount={}",
            event.userId(), event.orderId(), event.amount().getAmount());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("USER_ACTION action=PAYMENT_FAIL userId={} orderId={} amount={} reason={}",
            event.userId(), event.orderId(), event.amount().getAmount(), event.reason());
    }
}
