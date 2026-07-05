package com.loopers.application.catalog.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.catalog.metrics.ProductMetrics;
import com.loopers.domain.catalog.metrics.ProductMetricsRepository;
import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import com.loopers.kafka.event.EventMessage;
import com.loopers.kafka.event.ProductLikeEventPayload;
import com.loopers.support.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class ProductMetricsEventService {

    private static final String TOPIC_CATALOG_EVENTS = "catalog-events";
    private static final Set<String> LIKE_EVENT_TYPES = Set.of("PRODUCT_LIKED", "PRODUCT_UNLIKED");

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    @Transactional
    public ProcessResult process(EventMessage message) {
        validateMessage(message);
        if (eventHandledRepository.exists(message.eventId())) {
            eventMetrics.recordKafkaConsumerDuplicate(TOPIC_CATALOG_EVENTS, message.eventType());
            return ProcessResult.DUPLICATE;
        }

        eventHandledRepository.save(new EventHandled(
            message.eventId(),
            TOPIC_CATALOG_EVENTS,
            message.eventType(),
            message.aggregateType(),
            message.aggregateId(),
            ZonedDateTime.now()
        ));

        if (!LIKE_EVENT_TYPES.contains(message.eventType())) {
            eventMetrics.recordKafkaConsumerSuccess(TOPIC_CATALOG_EVENTS, message.eventType());
            return ProcessResult.IGNORED;
        }

        ProductLikeEventPayload payload = deserializePayload(message);
        ProductMetrics metrics = productMetricsRepository.findByProductId(payload.productId())
            .orElseGet(() -> new ProductMetrics(payload.productId()));
        boolean updated = metrics.applyLikeCount(payload.likeCount(), payload.occurredAt());
        if (!updated) {
            eventMetrics.recordKafkaConsumerSuccess(TOPIC_CATALOG_EVENTS, message.eventType());
            return ProcessResult.STALE;
        }

        productMetricsRepository.save(metrics);
        eventMetrics.recordProductMetricsUpdate(message.eventType());
        eventMetrics.recordKafkaConsumerSuccess(TOPIC_CATALOG_EVENTS, message.eventType());
        return ProcessResult.UPDATED;
    }

    private ProductLikeEventPayload deserializePayload(EventMessage message) {
        try {
            return objectMapper.readValue(message.payload(), ProductLikeEventPayload.class);
        } catch (JsonProcessingException e) {
            eventMetrics.recordKafkaConsumerFailure(TOPIC_CATALOG_EVENTS, message.eventType());
            throw new IllegalArgumentException("상품 메트릭 이벤트 payload 해석에 실패했습니다.", e);
        }
    }

    private void validateMessage(EventMessage message) {
        if (message == null || isBlank(message.eventId()) || isBlank(message.eventType())) {
            eventMetrics.recordKafkaConsumerFailure(TOPIC_CATALOG_EVENTS, "UNKNOWN");
            throw new IllegalArgumentException("이벤트 메시지는 필수입니다.");
        }
        if (isBlank(message.aggregateType()) || isBlank(message.aggregateId()) || isBlank(message.payload())) {
            eventMetrics.recordKafkaConsumerFailure(TOPIC_CATALOG_EVENTS, message.eventType());
            throw new IllegalArgumentException("이벤트 메시지의 aggregate와 payload는 필수입니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum ProcessResult {
        UPDATED,
        STALE,
        DUPLICATE,
        IGNORED
    }
}
