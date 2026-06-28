package com.loopers.application.event.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.catalog.like.ProductLikeChangedApplicationEvent;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.kafka.event.ProductLikeEventPayload;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class ProductLikeEventOutboxListener {

    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ProductLikeChangedApplicationEvent event) {
        ProductLikeEventPayload payload = new ProductLikeEventPayload(
            event.productId(),
            event.userId(),
            event.liked(),
            event.likeCount(),
            event.occurredAt()
        );
        eventOutboxRepository.save(new EventOutbox(
            EventOutbox.TOPIC_CATALOG_EVENTS,
            String.valueOf(event.productId()),
            event.eventType(),
            EventOutbox.AGGREGATE_PRODUCT,
            String.valueOf(event.productId()),
            serialize(payload)
        ));
    }

    private String serialize(ProductLikeEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "상품 좋아요 이벤트 payload 생성에 실패했습니다.");
        }
    }
}
