package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.LikeChangedEvent;
import com.loopers.domain.order.OrderPaidEvent;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 아웃박스에 적재한다. BEFORE_COMMIT 이라 도메인 변경과 같은 트랜잭션에서 커밋되어
 * "도메인은 커밋됐는데 아웃박스는 없음" 또는 그 반대가 생기지 않는다(원자성). 발행 자체는 릴레이가 담당.
 * ORDER_FAILED 는 메트릭 집계와 무관해 브리지하지 않고 내부 이벤트로만 둔다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventHandler {

    static final String TOPIC_CATALOG = "catalog-events";
    static final String TOPIC_ORDER = "order-events";
    static final String TYPE_LIKE_CHANGED = "LIKE_CHANGED";
    static final String TYPE_PRODUCT_VIEWED = "PRODUCT_VIEWED";
    static final String TYPE_ORDER_PAID = "ORDER_PAID";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(LikeChangedEvent event) {
        append(event.eventId(), "product", event.productId(), TOPIC_CATALOG, TYPE_LIKE_CHANGED, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(ProductViewedEvent event) {
        append(event.eventId(), "product", event.productId(), TOPIC_CATALOG, TYPE_PRODUCT_VIEWED, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(OrderPaidEvent event) {
        append(event.eventId(), "order", event.orderId(), TOPIC_ORDER, TYPE_ORDER_PAID, event);
    }

    private void append(String eventId, String aggregateType, Long aggregateId, String topic, String eventType, Object payload) {
        String json = serialize(new EventEnvelope(eventId, eventType, aggregateId, payload));
        outboxRepository.save(OutboxModel.of(eventId, aggregateType, aggregateId, topic, eventType, json));
    }

    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "이벤트 직렬화 실패: " + envelope.eventType());
        }
    }
}