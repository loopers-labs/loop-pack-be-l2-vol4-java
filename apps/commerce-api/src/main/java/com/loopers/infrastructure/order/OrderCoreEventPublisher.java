package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.order.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OrderCoreEventPublisher implements OrderEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(OrderCreatedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
