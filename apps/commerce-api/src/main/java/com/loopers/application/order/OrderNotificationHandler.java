package com.loopers.application.order;

import com.loopers.domain.order.event.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class OrderNotificationHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPlacedEvent event) {
        log.info("order notification: notify buyer userId={}, orderId={}, finalAmount={}",
            event.userId(), event.orderId(), event.finalAmount());
    }
}
