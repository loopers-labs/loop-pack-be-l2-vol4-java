package com.loopers.application.log;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.product.event.ProductViewedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동(조회·좋아요·주문·결제)을 서버 레벨로 기록하는 리스너. 부가 로직이라 이벤트로 분리한다.
 *
 * <ul>
 *   <li><b>AFTER_COMMIT</b>: 커밋된 행동만 남긴다(롤백된 주문/좋아요는 로그하지 않음).</li>
 *   <li><b>{@code fallbackExecution=true}</b>: 상품 조회는 읽기 경로(SUPPORTS·캐시 히트)라 트랜잭션이 없을 수 있다.
 *       이 옵션이 없으면 트랜잭션 없는 이벤트는 폐기된다 → true 로 두어 트랜잭션이 없으면 즉시 실행한다.</li>
 *   <li><b>동기(@Async 아님)</b>: 요청 스레드에서 실행해 MDC(trace_id) 상관관계를 보존한다. 느린 부분(로그 I/O)은
 *       logback AsyncAppender 가 백그라운드로 flush 하므로, 리스너는 동기여도 메인 응답을 붙잡지 않는다.</li>
 * </ul>
 *
 * <p>전용 로거 {@code user-action} 으로 찍어 AsyncAppender 로 라우팅한다. 실무의 본격 행동 스트림(분석·추천)은
 * Kafka 로 전파하는 게 정석이며, 여기 저볼륨 서버 로그는 Step 2 에서 Kafka 이벤트로 승격할 수 있다.</p>
 */
@Component
public class UserActionLogListener {

    private static final Logger log = LoggerFactory.getLogger("user-action");

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onViewed(ProductViewedEvent event) {
        log.info("action=PRODUCT_VIEWED userId={} productId={} at={}",
                event.userId(), event.productId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLiked(ProductLikedEvent event) {
        log.info("action=PRODUCT_LIKED userId={} productId={} at={}",
                event.userId(), event.productId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUnliked(ProductUnlikedEvent event) {
        log.info("action=PRODUCT_UNLIKED userId={} productId={} at={}",
                event.userId(), event.productId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("action=ORDER_PLACED userId={} orderId={} at={}",
                event.userId(), event.orderId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("action=PAYMENT_COMPLETED userId={} orderId={} amount={} at={}",
                event.userId(), event.orderId(), event.amount(), event.occurredAt());
    }
}
