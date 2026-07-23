package com.loopers.tddstudy.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.tddstudy.domain.like.event.ProductLikedEvent;
import com.loopers.tddstudy.domain.like.event.ProductUnlikedEvent;
import com.loopers.tddstudy.domain.order.event.OrderCompletedEvent;
import com.loopers.tddstudy.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.tddstudy.infrastructure.outbox.OutboxMessage;
import com.loopers.tddstudy.messaging.CatalogEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class OutboxAppender {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxAppender(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLiked(ProductLikedEvent e) {
        append(new CatalogEvent(newId(), "PRODUCT_LIKED", e.productId(), 1, now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUnliked(ProductUnlikedEvent e) {
        append(new CatalogEvent(newId(), "PRODUCT_UNLIKED", e.productId(), 1, now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrder(OrderCompletedEvent e) {
        for (OrderCompletedEvent.Line line : e.lines()) {
            append(new CatalogEvent(newId(), "ORDER_SALES", line.productId(), line.quantity(), now()));
        }
    }

    private void append(CatalogEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(new OutboxMessage(
                    event.eventId(), CatalogEvent.TOPIC,
                    String.valueOf(event.productId()), payload));
        } catch (Exception ex) {
            throw new RuntimeException("Outbox 직렬화 실패", ex);
        }
    }

    private String newId() { return UUID.randomUUID().toString(); }
    private long now() { return System.currentTimeMillis(); }
}
