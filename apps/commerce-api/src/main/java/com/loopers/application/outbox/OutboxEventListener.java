package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.product.event.ProductViewedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 시스템 간 전파가 필요한 도메인 이벤트를 Outbox 테이블에 적재한다.
 * 도메인 트랜잭션과 같은 트랜잭션에서 동작해야 하므로 AFTER_COMMIT이 아닌 일반 @EventListener를 사용한다
 * — outbox insert 실패 시 도메인 변경도 함께 롤백되어야 발행 보장이 성립한다.
 */
@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @EventListener
    public void on(ProductLikedEvent event) {
        saveCatalogEvent(event.productId(), CatalogEventPayload.PRODUCT_LIKED, event.userId(), event.productId());
    }

    @EventListener
    public void on(ProductUnlikedEvent event) {
        saveCatalogEvent(event.productId(), CatalogEventPayload.PRODUCT_UNLIKED, event.userId(), event.productId());
    }

    @EventListener
    public void on(ProductViewedEvent event) {
        saveCatalogEvent(event.productId(), CatalogEventPayload.PRODUCT_VIEWED, null, event.productId());
    }

    @EventListener
    public void on(PaymentCompletedEvent event) {
        List<OrderEventPayload.Item> items = event.items().stream()
            .map(item -> new OrderEventPayload.Item(item.productId(), item.quantity()))
            .toList();

        String eventId = UUID.randomUUID().toString();
        OrderEventPayload payload = new OrderEventPayload(eventId, OrderEventPayload.ORDER_PAID, event.orderId(), event.userId(), items);
        save(eventId, KafkaTopics.ORDER_EVENTS, event.orderId().toString(), payload);
    }

    @EventListener
    public void on(CouponIssueRequestedEvent event) {
        String eventId = UUID.randomUUID().toString();
        CouponIssueRequestPayload payload = new CouponIssueRequestPayload(
            eventId, CouponIssueRequestPayload.ISSUE_REQUESTED, event.requestId(), event.userId(), event.couponId());
        save(eventId, KafkaTopics.COUPON_ISSUE_REQUESTS, event.couponId().toString(), payload);
    }

    private void saveCatalogEvent(Long messageKey, String eventType, Long userId, Long productId) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventPayload payload = new CatalogEventPayload(eventId, eventType, userId, productId);
        save(eventId, KafkaTopics.CATALOG_EVENTS, messageKey.toString(), payload);
    }

    private void save(String eventId, String topic, String messageKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(new OutboxEventModel(eventId, topic, messageKey, json));
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "이벤트 페이로드 직렬화에 실패했습니다.");
        }
    }
}
