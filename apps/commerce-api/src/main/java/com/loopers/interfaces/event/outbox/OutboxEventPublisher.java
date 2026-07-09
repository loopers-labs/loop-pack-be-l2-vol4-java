package com.loopers.interfaces.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.interfaces.event.like.LikedEvent;
import com.loopers.interfaces.event.like.UnlikedEvent;
import com.loopers.interfaces.event.order.OrderCreatedEvent;
import com.loopers.interfaces.event.product.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final String CATALOG_EVENTS = "catalog-events";
    private static final String ORDER_EVENTS = "order-events";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(LikedEvent event) {
        outboxRepository.save(OutboxModel.create(CATALOG_EVENTS, event.productId().toString(), "LikedEvent", wrap("LikedEvent", event)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(UnlikedEvent event) {
        outboxRepository.save(OutboxModel.create(CATALOG_EVENTS, event.productId().toString(), "UnlikedEvent", wrap("UnlikedEvent", event)));
    }

    @Transactional
    @EventListener
    public void handle(ProductViewedEvent event) {
        outboxRepository.save(OutboxModel.create(CATALOG_EVENTS, event.productId().toString(), "ProductViewedEvent", wrap("ProductViewedEvent", event)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderCreatedEvent event) {
        outboxRepository.save(OutboxModel.create(ORDER_EVENTS, event.orderId().toString(), "OrderCreatedEvent", wrap("OrderCreatedEvent", event)));
    }

    private String wrap(String eventType, Object event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventType", eventType);
            envelope.put("data", event);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패: " + eventType, e);
        }
    }
}
