package com.loopers.application.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetricsModel;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * order-events 처리 — 결제 완료(ORDER_PAID)된 주문의 상품별 수량만큼 product_metrics.sales_count를 증가시킨다.
 * event_handled 멱등 처리는 catalog-events와 동일한 테이블/개념을 공유한다 (토픽에 무관하게 eventId로 전역 유일).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventFacade {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String rawPayload) {
        OrderEventPayload payload = parse(rawPayload);

        if (eventHandledRepository.existsByEventId(payload.eventId())) {
            log.info("이미 처리된 이벤트 — eventId={}", payload.eventId());
            return;
        }

        switch (payload.eventType()) {
            case OrderEventPayload.ORDER_PAID -> applySales(payload.items());
            default -> throw new IllegalArgumentException("지원하지 않는 order 이벤트 타입입니다: " + payload.eventType());
        }

        eventHandledRepository.save(new EventHandledModel(payload.eventId()));
    }

    private void applySales(List<OrderEventPayload.Item> items) {
        for (OrderEventPayload.Item item : items) {
            ProductMetricsModel metrics = productMetricsRepository.findByProductId(item.productId())
                .orElseGet(() -> productMetricsRepository.save(new ProductMetricsModel(item.productId())));
            metrics.incrementSalesCount(item.quantity());
        }
    }

    private OrderEventPayload parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, OrderEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("order 이벤트 페이로드 파싱에 실패했습니다: " + rawPayload, e);
        }
    }
}
