package com.loopers.interfaces.event;

import com.loopers.interfaces.event.order.OrderCreatedEvent;
import com.loopers.interfaces.event.product.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserActionLoggingHandler {

    @Async
    @EventListener
    public void handle(ProductViewedEvent event) {
        log.info("[ProductViewed] memberId={}, productId={}", event.memberId(), event.productId());
    }

    @Async
    @EventListener
    public void handle(OrderCreatedEvent event) {
        log.info("[OrderCreated] memberId={}, orderId={}, totalPrice={}", event.memberId(), event.orderId(), event.totalPrice());
    }
}
