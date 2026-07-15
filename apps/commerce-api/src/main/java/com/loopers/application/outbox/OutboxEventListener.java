package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueRequestedEvent;
import com.loopers.application.like.LikeChangedEvent;
import com.loopers.application.payment.PaymentCompletedEvent;
import com.loopers.confg.kafka.message.EventEnvelope;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transactional Outbox 기록 리스너 — 시스템 간 전파가 필요한 이벤트를 outbox 테이블에 기록한다.
 *
 * <p><strong>{@link TransactionPhase#BEFORE_COMMIT}</strong> — 커밋 직전, 비즈니스 트랜잭션에
 * <em>참여</em>하여 INSERT 한다. 비즈니스 변경과 outbox 행이 원자적으로 함께 커밋/롤백되는 것이
 * 이 패턴의 핵심이다(AFTER_COMMIT 이면 커밋~기록 사이 유실 창이 생겨 패턴이 무너진다).
 *
 * <p>따라서 outbox 기록 실패는 비즈니스 트랜잭션 롤백으로 이어진다 — "이벤트를 기록 못 하면
 * 작업 자체를 실패시킨다"는 의도된 선택이다(전달 보장 > 가용성).
 *
 * <p>발행부(like/handleCallback)는 이 리스너의 존재를 모른다 — Step 1 이벤트에 리스너만 추가됐다.
 * 실제 Kafka 전송은 {@link OutboxRelayScheduler}가 수행한다.
 */
@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Value("${commerce-events.topics.catalog}")
    private String catalogTopic;

    @Value("${commerce-events.topics.order}")
    private String orderTopic;

    @Value("${commerce-events.topics.coupon-issue}")
    private String couponIssueTopic;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLikeChanged(LikeChangedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", event.productId());
        payload.put("type", event.type().name());

        writeOutbox(catalogTopic, event.productId().toString(), "LIKE_CHANGED", payload);
    }

    /**
     * 결제 완료 — 소비측(streamer)이 상품별 판매량을 집계할 수 있도록 주문 항목(productId/quantity)을
     * payload 에 함께 싣는다(Event-Carried State Transfer). 소비자가 발행자 DB/API 를 되조회하지 않게 한다.
     * BEFORE_COMMIT 이라 같은 트랜잭션에서 주문을 조회한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        OrderModel order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR,
                "[orderId = " + event.orderId() + "] outbox 기록 중 주문을 찾을 수 없습니다."));

        List<Map<String, Object>> items = order.getItems().stream()
            .map(item -> Map.<String, Object>of(
                "productId", item.getProductId(),
                "quantity", item.getQuantity(),
                "price", item.getProductPrice()   // 랭킹 점수(price*amount) 계산을 위해 단가를 함께 싣는다
            ))
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", event.orderId());
        payload.put("transactionKey", event.transactionKey());
        payload.put("amount", event.amount());
        payload.put("items", items);

        writeOutbox(orderTopic, event.orderId().toString(), "PAYMENT_COMPLETED", payload);
    }

    /**
     * 선착순 쿠폰 발급 요청 — 접수 행(coupon_issue_requests)과 같은 트랜잭션으로 기록되어
     * "202 를 받은 요청은 반드시 처리된다"를 보장한다. requestId 를 eventId 로 사용해
     * 접수증-메시지-처리기록이 하나의 식별자로 이어진다.
     *
     * <p>key=userId — 선착순 판정은 이미 Redis 예약(reserve) 단계에서 끝나므로, 이 시점의
     * Kafka 메시지는 "당첨 확정된 결과의 DB 반영"일 뿐이라 쿠폰별 순서 보장이 불필요하다.
     * userId 로 분산해 여러 파티션에서 병렬 소비할 수 있다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCouponIssueRequested(CouponIssueRequestedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", event.requestId());
        payload.put("userId", event.userId());
        payload.put("couponId", event.couponId());

        writeOutbox(couponIssueTopic, event.userId().toString(), "COUPON_ISSUE_RESERVED", payload, event.requestId());
    }

    private void writeOutbox(String topic, String partitionKey, String eventType, Map<String, Object> payload) {
        writeOutbox(topic, partitionKey, eventType, payload, null);
    }

    private void writeOutbox(String topic, String partitionKey, String eventType, Map<String, Object> payload, String eventId) {
        EventEnvelope envelope = eventId == null
            ? EventEnvelope.of(eventType, payload)
            : EventEnvelope.of(eventId, eventType, payload);
        try {
            String message = objectMapper.writeValueAsString(envelope);
            outboxEventRepository.save(new OutboxEvent(envelope.eventId(), topic, partitionKey, eventType, message));
        } catch (JsonProcessingException e) {
            // 직렬화 실패 = 이벤트 기록 불가 → 비즈니스 트랜잭션 롤백 (전달 보장 우선)
            throw new CoreException(ErrorType.INTERNAL_ERROR, "outbox 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}
