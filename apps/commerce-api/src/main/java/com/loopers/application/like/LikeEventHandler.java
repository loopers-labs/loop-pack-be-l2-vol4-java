package com.loopers.application.like;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.event.LikeChangedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeEventHandler {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 좋아요 변경을 좋아요와 "같은 트랜잭션" 에서 outbox 에 적재한다(새 TX 없음).
     * AFTER_COMMIT+REQUIRES_NEW(스레드당 커넥션 2개) 대신 inline @EventListener 로 두어
     * (1) 동시성 커넥션 증폭을 없애고, (2) 비즈니스 변경과 outbox INSERT 를 한 원자 단위로 묶는다(dual-write 유실 방지).
     * 실제 집계(product_metrics)는 이 outbox 를 소비하는 streamer 가 책임진다 — 장애 격리.
     */
    @EventListener
    public void on(LikeChangedEvent event) {
        outboxEventRepository.append(toOutboxEvent(event));
    }

    private OutboxEvent toOutboxEvent(LikeChangedEvent event) {
        try {
            String aggregateType = "Product";
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(event);
            return OutboxEvent.of(aggregateType, event.productId(), event.type().name(), eventId, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
