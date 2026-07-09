package com.loopers.application.order;

import com.loopers.domain.order.OrderPaidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 결제 완료 알림. OrderPaidEvent 에 UserActivityEventHandler 와 독립적으로 반응해,
 * 한 이벤트에 리스너를 더하는 것만으로 후속 로직이 확장됨을 보인다. (현재 알림은 로그 스텁)
 */
@Slf4j
@Component
public class OrderNotificationEventHandler {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("[notification] 주문 결제 완료 알림 전송 - orderId={}, userId={}, finalAmount={}",
                event.orderId(), event.userId(), event.finalAmount());
    }
}