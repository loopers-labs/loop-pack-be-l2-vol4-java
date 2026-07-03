package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueRequestedEvent;
import com.loopers.confg.kafka.Topics;
import com.loopers.domain.order.OrderCompletedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.productlike.ProductLikedEvent;
import com.loopers.domain.productlike.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * 도메인 이벤트를 outbox 테이블에 기록하는 리스너.
 * <p>
 * {@code BEFORE_COMMIT}으로 동작해 비즈니스 트랜잭션과 <b>같은 트랜잭션</b>에서 outbox 행을 저장한다.
 * 덕분에 "비즈니스 데이터 커밋"과 "발행 대상 이벤트 기록"이 원자적으로 함께 커밋되거나 함께 롤백된다.
 * 실제 Kafka 발행은 커밋과 분리된 {@link OutboxRelay} 폴러가 담당한다(at-least-once).
 * <p>
 * Step 1의 도메인 이벤트를 그대로 재사용한다("하나의 사실, 여러 소비자"). 예: ProductLiked는
 * (Step1) 비동기 집계 리스너 + (Step2) 이 outbox 기록 리스너가 함께 구독한다.
 */
@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onProductLiked(ProductLikedEvent event) {
        appendCatalog("LIKED", event.productId(), event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onProductUnliked(ProductUnlikedEvent event) {
        appendCatalog("UNLIKED", event.productId(), event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        String eventId = UUID.randomUUID().toString();
        var lines = event.lines().stream()
            .map(l -> new OrderEventPayload.Line(l.productId(), l.quantity()))
            .toList();
        OrderEventPayload payload = new OrderEventPayload(eventId, event.orderId(), event.userId(), lines);
        append(Topics.ORDER_EVENTS, String.valueOf(event.orderId()), "OrderCompleted", eventId, payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCouponIssueRequested(CouponIssueRequestedEvent event) {
        // eventId = requestId: 추적/상태 조회 키와 outbox/메시지 식별자를 일치시킨다. 파티션 키 = couponId.
        append(Topics.COUPON_ISSUE_REQUESTS, String.valueOf(event.couponId()),
            "CouponIssueRequested", event.requestId(), event);
    }

    private void appendCatalog(String type, Long productId, Long userId) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventPayload payload = new CatalogEventPayload(eventId, type, productId, userId);
        append(Topics.CATALOG_EVENTS, String.valueOf(productId), type, eventId, payload);
    }

    private void append(String topic, String partitionKey, String eventType, String eventId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(new OutboxEvent(eventId, topic, partitionKey, eventType, json));
        } catch (JsonProcessingException e) {
            // 직렬화 실패 시 비즈니스 트랜잭션을 함께 롤백시켜 이벤트 누락(불완전 커밋)을 막는다.
            throw new IllegalStateException("outbox payload 직렬화 실패: " + eventType, e);
        }
    }
}
