package com.loopers.tddstudy.application.order;

import com.loopers.tddstudy.domain.order.event.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLogging(OrderCompletedEvent event) {
        log.info("[행동로깅] 주문완료 userId={}, orderId={}", event.userId(), event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotification(OrderCompletedEvent event) {
        log.info("[알림] 주문완료 안내 발송 orderId={}, amount={}", event.orderId(), event.totalAmount());
    }
}
