package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.event.CouponIssueRequested;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.product.event.ProductViewed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private static final String CATALOG = "catalog-events";
    private static final String ORDER = "order-events";
    private static final String COUPON_ISSUE = "coupon-issue-requests";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // 동기 @EventListener — 도메인 @Transactional 안에서 실행되어 outbox INSERT 가 도메인 변경과 원자적
    @EventListener
    public void on(LikeAdded e) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventPayload payload = new CatalogEventPayload(
            eventId, "LikeAdded", e.productId(), e.likeCount(), e.version(), e.occurredAt().toString());
        append(CATALOG, String.valueOf(e.productId()), "LikeAdded", eventId, payload);
    }

    @EventListener
    public void on(LikeRemoved e) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventPayload payload = new CatalogEventPayload(
            eventId, "LikeRemoved", e.productId(), e.likeCount(), e.version(), e.occurredAt().toString());
        append(CATALOG, String.valueOf(e.productId()), "LikeRemoved", eventId, payload);
    }

    @EventListener
    public void on(OrderPlaced e) {
        String eventId = UUID.randomUUID().toString();
        var lines = e.lines().stream()
            .map(l -> new OrderEventPayload.Line(l.productId(), l.quantity()))
            .toList();
        OrderEventPayload payload = new OrderEventPayload(eventId, "OrderPlaced", e.orderId(), lines, e.occurredAt().toString());
        append(ORDER, String.valueOf(e.orderId()), "OrderPlaced", eventId, payload);
    }

    // 조회는 읽기 경로라 묶일 도메인 tx 가 없다 — 응답 경로 밖(@Async)에서 outbox 전용 짧은 tx 로 적재.
    // 발행 경로는 Outbox 단일화 유지, 조회 응답에는 쓰기 지연이 붙지 않는다.
    @Async("eventExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @EventListener
    public void on(ProductViewed e) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventPayload payload = new CatalogEventPayload(
            eventId, "ProductViewed", e.productId(), 0L, 0L, e.occurredAt().toString());
        append(CATALOG, String.valueOf(e.productId()), "ProductViewed", eventId, payload);
    }

    // 선착순 발급요청 — eventId 는 requestId 를 그대로 사용(consumer 멱등 키 = requestId 통일)
    @EventListener
    public void on(CouponIssueRequested e) {
        CouponIssuePayload payload = new CouponIssuePayload(
            e.requestId(), e.couponId(), e.userId(), e.occurredAt().toString());
        append(COUPON_ISSUE, String.valueOf(e.couponId()), "CouponIssueRequested", e.requestId(), payload);
    }

    private void append(String topic, String key, String type, String eventId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("outbox payload 직렬화 실패", ex); // 도메인 tx 롤백 → 안전
        }
        outboxRepository.save(OutboxEvent.pending(eventId, topic, key, type, json));
    }
}
