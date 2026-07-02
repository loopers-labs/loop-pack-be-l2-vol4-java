package com.loopers.application.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderEventFacade {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;

    /**
     * 한 배치를 한 트랜잭션으로 처리한다. 처음 보는 이벤트면 표시(markIfFirst)와 판매량 반영을 같은 TX 로 묶는다.
     * 판매량은 수량만큼 누적(delta)되는 commutative 값이라 순서와 무관하며, 멱등이 재전송/배치 재처리 중복을 막는다.
     */
    @Transactional
    public void handle(List<OrderEventMessage> messages) {
        for (OrderEventMessage message : messages) {
            if (eventHandledRepository.markIfFirst(message.eventId())) {
                applySales(message);
            }
        }
    }

    private void applySales(OrderEventMessage message) {
        JsonNode lines = message.data().get("lines");
        if (lines == null) {
            return;
        }
        for (JsonNode line : lines) {
            long productId = line.get("productId").asLong();
            long quantity = line.get("quantity").asLong();
            productMetricsRepository.applySalesDelta(productId, quantity);
        }
    }
}
