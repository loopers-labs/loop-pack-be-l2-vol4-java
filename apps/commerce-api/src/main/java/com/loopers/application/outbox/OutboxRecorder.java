package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.payment.PaymentFailedEvent;
import com.loopers.domain.payment.PaymentSucceededEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 스프링 이벤트 중 시스템 밖(commerce-streamer)으로 전파할 것들을 outbox 에 기록한다.
 * 일반 @EventListener 는 발행자의 트랜잭션 안에서 동기로 실행되므로, 사실 저장과 outbox 기록이
 * 함께 커밋/롤백된다 — Transactional Outbox 의 원자성이 성립하는 지점.
 * 트랜잭션이 없는 경로(상품 조회)는 @Transactional 이 새 트랜잭션을 연다.
 */
@RequiredArgsConstructor
@Component
public class OutboxRecorder {

    public static final String CATALOG_TOPIC = "catalog-events"; // key=productId
    public static final String ORDER_TOPIC = "order-events";     // key=orderId

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @EventListener
    public void on(ProductLikedEvent event) {
        record(CATALOG_TOPIC, event.productId(), event);
    }

    @Transactional
    @EventListener
    public void on(ProductUnlikedEvent event) {
        record(CATALOG_TOPIC, event.productId(), event);
    }

    @Transactional
    @EventListener
    public void on(ProductViewedEvent event) {
        record(CATALOG_TOPIC, event.productId(), event);
    }

    @Transactional
    @EventListener
    public void on(OrderCreatedEvent event) {
        record(ORDER_TOPIC, event.orderId(), event);
    }

    @Transactional
    @EventListener
    public void on(PaymentSucceededEvent event) {
        record(ORDER_TOPIC, event.orderId(), event);
    }

    @Transactional
    @EventListener
    public void on(PaymentFailedEvent event) {
        record(ORDER_TOPIC, event.orderId(), event);
    }

    private void record(String topic, Long partitionKey, Object event) {
        String eventId = UUID.randomUUID().toString();
        String eventType = event.getClass().getSimpleName();
        OutboxMessage message = new OutboxMessage(eventId, eventType, event);
        try {
            String payload = objectMapper.writeValueAsString(message);
            outboxEventRepository.save(new OutboxEvent(eventId, topic, String.valueOf(partitionKey), eventType, payload));
        } catch (JsonProcessingException e) {
            // 직렬화 실패는 프로그래밍 오류 — 조용히 삼키면 이벤트만 유실되므로 트랜잭션째 실패시킨다.
            throw new IllegalStateException("outbox 이벤트 직렬화에 실패했습니다: " + eventType, e);
        }
    }

    /** Kafka 로 나가는 메시지 봉투 — eventId 는 Consumer 의 멱등 판정 키가 된다. */
    record OutboxMessage(String eventId, String type, Object payload) {
    }
}
