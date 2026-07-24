package com.loopers.application.activitylog;

import com.loopers.domain.order.OrderCompletedEvent;
import com.loopers.domain.productlike.ProductLikedEvent;
import com.loopers.domain.productlike.ProductUnlikedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동을 서버 레벨에서 기록하는 리스너(현재는 로그 기록 수준).
 * <p>
 * 여러 도메인의 "사실" 이벤트(주문 완료, 좋아요/취소)를 각각 구독한다. 특히 {@link ProductLikedEvent}는
 * 집계 리스너(ProductLikeEventHandler)도 구독하는 이벤트로, "하나의 사실을 여러 소비자가 각자 소비"하는 예다.
 * <p>
 * 모든 핸들러는 동기 AFTER_COMMIT + 예외 삼킴이다. 로그만 남기므로(DB/외부 I/O 없음) 동기로 충분하고,
 * 본 트랜잭션이 커밋된 뒤에만 기록하며, 로깅 실패가 본 흐름의 응답을 오염시키지 않는다.
 */
@Component
public class UserActivityLogHandler {

    private static final Logger log = LoggerFactory.getLogger(UserActivityLogHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        logActivity("ORDER", event.userId(), "orderId=" + event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductLiked(ProductLikedEvent event) {
        logActivity("LIKE", event.userId(), "productId=" + event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUnliked(ProductUnlikedEvent event) {
        logActivity("UNLIKE", event.userId(), "productId=" + event.productId());
    }

    private void logActivity(String action, Long userId, String detail) {
        try {
            log.info("유저 행동 — action={}, userId={}, {}", action, userId, detail);
        } catch (Exception e) {
            log.error("유저 행동 로깅 실패 — action={}, userId={}", action, userId, e);
        }
    }
}
