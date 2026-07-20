package com.loopers.application.event.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.kafka.event.ProductViewEventPayload;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductViewEventPublisher {

    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventOutbox publish(Long productId, String userId) {
        return publishInternal(productId, userId, ZonedDateTime.now());
    }

    public void publishSafely(Long productId, String userId) {
        try {
            publishInternal(productId, userId, ZonedDateTime.now());
        } catch (RuntimeException e) {
            log.warn("Failed to publish product view event. productId={}, userId={}", productId, userId, e);
        }
    }

    EventOutbox publish(Long productId, String userId, ZonedDateTime occurredAt) {
        return publishInternal(productId, userId, occurredAt);
    }

    private EventOutbox publishInternal(Long productId, String userId, ZonedDateTime occurredAt) {
        ProductViewEventPayload payload = new ProductViewEventPayload(productId, userId, occurredAt);
        return eventOutboxRepository.save(new EventOutbox(
            EventOutbox.TOPIC_CATALOG_EVENTS,
            String.valueOf(productId),
            EventOutbox.EVENT_PRODUCT_VIEWED,
            EventOutbox.AGGREGATE_PRODUCT,
            String.valueOf(productId),
            serialize(payload)
        ));
    }

    private String serialize(ProductViewEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "상품 조회 이벤트 payload 생성에 실패했습니다.");
        }
    }
}
