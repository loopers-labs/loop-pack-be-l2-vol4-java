package com.loopers.application.order;

import com.loopers.domain.order.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 완료 시 알림을 담당하는 리스너(현재는 로그 기록 수준).
 * <p>
 * 설계 결정:
 * <ul>
 *   <li>{@code AFTER_COMMIT}: 주문이 실제로 커밋된 뒤에만 알림한다. 주문 실패(롤백) 시 알림이 나가지 않는다.</li>
 *   <li>동기 실행: 로그만 남기고 DB/외부 I/O를 하지 않으므로 요청 스레드에서 동기로 처리해도 부담이 없다.
 *       (실제 외부 알림 발송으로 바뀌면 그때 이 리스너만 @Async로 전환하면 된다.)</li>
 *   <li>예외 삼킴: 알림 실패가 이미 커밋된 주문의 응답을 오염시키지 않도록 여기서 잡는다.
 *       (동기 AFTER_COMMIT 리스너의 예외는 호출자에게 전파되기 때문이다.)</li>
 * </ul>
 */
@Component
public class OrderNotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        try {
            log.info("주문 완료 알림 발송 — orderId={}, userId={}, finalPrice={}",
                event.orderId(), event.userId(), event.finalPrice());
        } catch (Exception e) {
            log.error("주문 완료 알림 실패 — orderId={}, userId={}", event.orderId(), event.userId(), e);
        }
    }
}
