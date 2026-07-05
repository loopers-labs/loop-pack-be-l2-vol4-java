package com.loopers.application.stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.stock.event.StockChangedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventHandler {

    private static final String AGGREGATE_TYPE = "Product";
    private static final String EVENT_TYPE = "STOCK_CHANGED";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 재고 변경을 재고 변경과 "같은 트랜잭션" 에서 outbox 에 적재한다 (LikeEventHandler 와 동일 패턴).
     * 관리자 재고 수정이 낙관적 락 충돌 등으로 롤백되면 outbox INSERT 도 함께 롤백 → 유령 이벤트 방지.
     * aggregateType=Product → catalog-events 토픽, aggregateId=productId → 같은 상품=같은 파티션(순서 보장).
     */
    @EventListener
    public void on(StockChangedEvent event) {
        outboxEventRepository.append(toOutboxEvent(event));
    }

    private OutboxEvent toOutboxEvent(StockChangedEvent event) {
        try {
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(event);
            return OutboxEvent.of(AGGREGATE_TYPE, event.productId(), EVENT_TYPE, eventId, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
