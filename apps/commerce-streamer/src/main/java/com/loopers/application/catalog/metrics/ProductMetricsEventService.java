package com.loopers.application.catalog.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.catalog.metrics.ProductMetrics;
import com.loopers.domain.catalog.metrics.ProductMetricsRepository;
import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import com.loopers.kafka.event.EventMessage;
import com.loopers.kafka.event.ProductLikeEventPayload;
import com.loopers.kafka.event.ProductViewEventPayload;
import com.loopers.support.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class ProductMetricsEventService {

    private static final String TOPIC_CATALOG_EVENTS = "catalog-events";
    private static final String TOPIC_ORDER_EVENTS = "order-events";
    private static final String EVENT_PRODUCT_VIEWED = "PRODUCT_VIEWED";
    private static final String EVENT_PRODUCT_LIKED = "PRODUCT_LIKED";
    private static final String EVENT_PRODUCT_UNLIKED = "PRODUCT_UNLIKED";
    private static final String EVENT_ORDER_PAID = "ORDER_PAID";
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        EVENT_PRODUCT_VIEWED,
        EVENT_PRODUCT_LIKED,
        EVENT_PRODUCT_UNLIKED,
        EVENT_ORDER_PAID
    );
    private static final ZoneId METRICS_ZONE = ZoneId.of("Asia/Seoul");

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    @Transactional
    public ProcessResult process(EventMessage message) {
        return process(TOPIC_CATALOG_EVENTS, message);
    }

    @Transactional
    public ProcessResult process(String topic, EventMessage message) {
        validateMessage(topic, message);
        if (eventHandledRepository.exists(message.eventId())) {
            eventMetrics.recordKafkaConsumerDuplicate(topic, message.eventType());
            return ProcessResult.DUPLICATE;
        }

        eventHandledRepository.save(new EventHandled(
            message.eventId(),
            topic,
            message.eventType(),
            message.aggregateType(),
            message.aggregateId(),
            ZonedDateTime.now()
        ));

        if (!SUPPORTED_EVENT_TYPES.contains(message.eventType())) {
            eventMetrics.recordKafkaConsumerSuccess(topic, message.eventType());
            return ProcessResult.IGNORED;
        }

        switch (message.eventType()) {
            case EVENT_PRODUCT_VIEWED -> applyProductViewed(topic, message);
            case EVENT_PRODUCT_LIKED -> applyProductLikeDelta(topic, message, 1L);
            case EVENT_PRODUCT_UNLIKED -> applyProductLikeDelta(topic, message, -1L);
            case EVENT_ORDER_PAID -> applyOrderPaid(topic, message);
            default -> {
                eventMetrics.recordKafkaConsumerSuccess(topic, message.eventType());
                return ProcessResult.IGNORED;
            }
        }

        eventMetrics.recordProductMetricsUpdate(message.eventType());
        eventMetrics.recordKafkaConsumerSuccess(topic, message.eventType());
        return ProcessResult.UPDATED;
    }

    private void applyProductViewed(String topic, EventMessage message) {
        ProductViewEventPayload payload = deserializePayload(topic, message, ProductViewEventPayload.class);
        ProductMetrics metrics = getMetrics(message, payload.productId());
        metrics.increaseViewCount();
        productMetricsRepository.save(metrics);
    }

    private void applyProductLikeDelta(String topic, EventMessage message, long delta) {
        ProductLikeEventPayload payload = deserializePayload(topic, message, ProductLikeEventPayload.class);
        ProductMetrics metrics = getMetrics(message, payload.productId());
        ZonedDateTime eventAt = payload.occurredAt() == null ? message.occurredAt() : payload.occurredAt();
        metrics.applyLikeDelta(delta, eventAt);
        productMetricsRepository.save(metrics);
    }

    private void applyOrderPaid(String topic, EventMessage message) {
        OrderPaidMetricsPayload payload = deserializePayload(topic, message, OrderPaidMetricsPayload.class);
        Map<Long, SalesAggregate> aggregates = aggregateSales(payload);
        for (Map.Entry<Long, SalesAggregate> entry : aggregates.entrySet()) {
            ProductMetrics metrics = getMetrics(message, entry.getKey());
            SalesAggregate aggregate = entry.getValue();
            metrics.increaseSales(aggregate.quantity(), aggregate.salesAmount());
            productMetricsRepository.save(metrics);
        }
    }

    private Map<Long, SalesAggregate> aggregateSales(OrderPaidMetricsPayload payload) {
        if (payload.items() == null || payload.items().isEmpty()) {
            return Map.of();
        }

        Map<Long, SalesAggregate> aggregates = new HashMap<>();
        for (OrderPaidMetricsPayload.Item item : payload.items()) {
            if (item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
                continue;
            }
            aggregates.compute(
                item.productId(),
                (productId, current) -> {
                    SalesAggregate base = current == null ? new SalesAggregate(0, 0L) : current;
                    return new SalesAggregate(
                        base.quantity() + item.quantity(),
                        base.salesAmount() + item.salesAmount()
                    );
                }
            );
        }
        return aggregates;
    }

    private ProductMetrics getMetrics(EventMessage message, Long productId) {
        LocalDate metricDate = message.occurredAt().withZoneSameInstant(METRICS_ZONE).toLocalDate();
        return productMetricsRepository.findByMetricDateAndProductId(metricDate, productId)
            .orElseGet(() -> new ProductMetrics(metricDate, productId));
    }

    private <T> T deserializePayload(String topic, EventMessage message, Class<T> payloadType) {
        try {
            return objectMapper.readValue(message.payload(), payloadType);
        } catch (JsonProcessingException e) {
            eventMetrics.recordKafkaConsumerFailure(topic, message.eventType());
            throw new IllegalArgumentException("상품 메트릭 이벤트 payload 해석에 실패했습니다.", e);
        }
    }

    private void validateMessage(String topic, EventMessage message) {
        if (message == null || isBlank(topic) || isBlank(message.eventId()) || isBlank(message.eventType())) {
            eventMetrics.recordKafkaConsumerFailure(topic == null ? "UNKNOWN" : topic, "UNKNOWN");
            throw new IllegalArgumentException("이벤트 메시지는 필수입니다.");
        }
        if (isBlank(message.aggregateType()) || isBlank(message.aggregateId()) || isBlank(message.payload())
            || message.occurredAt() == null) {
            eventMetrics.recordKafkaConsumerFailure(topic, message.eventType());
            throw new IllegalArgumentException("이벤트 메시지의 aggregate, payload, occurredAt은 필수입니다.");
        }
        if (!TOPIC_CATALOG_EVENTS.equals(topic) && !TOPIC_ORDER_EVENTS.equals(topic)) {
            eventMetrics.recordKafkaConsumerFailure(topic, message.eventType());
            throw new IllegalArgumentException("상품 메트릭 이벤트 topic이 아닙니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum ProcessResult {
        UPDATED,
        DUPLICATE,
        IGNORED
    }

    private record SalesAggregate(Integer quantity, Long salesAmount) {
    }
}
