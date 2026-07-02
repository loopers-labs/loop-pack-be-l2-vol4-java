package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.interfaces.api.config.KafkaTopicConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Step 1 이 이미 발행하는 도메인 이벤트를 주워 outbox 행으로 변환한다(전파 대상·토픽 매핑을 한 곳에 집중).
 *
 * <p><b>phase=BEFORE_COMMIT</b>: 발행 트랜잭션이 아직 열려 있는 시점에 실행되므로 여기서의 {@code save} 는
 * 상태변경과 <b>같은 트랜잭션</b>에 참여한다 → 커밋 시 함께, 롤백 시 함께. 이것이 outbox 원자성의 핵심이다.
 * (AFTER_COMMIT 이면 별도 TX 가 되어 다시 dual-write 가 된다. 그래서 여기엔 REQUIRES_NEW 를 <b>붙이면 안 된다</b>.)</p>
 *
 * <p>매핑/직렬화가 실패하면 예외가 전파돼 발행 TX 자체가 롤백된다 — "전파 의도를 기록하지 못하면 상태변경도 커밋하지 않는다"
 * 는 원자성을 지키는 의도된 결합이다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onProductLiked(ProductLikedEvent event) {
        enqueueCatalog(CatalogEventType.PRODUCT_LIKED, event.productId(), event.userId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onProductUnliked(ProductUnlikedEvent event) {
        enqueueCatalog(CatalogEventType.PRODUCT_UNLIKED, event.productId(), event.userId(), event.occurredAt());
    }

    /**
     * 결제 성공 1건을 <b>상품별 order-events 행 N개</b>로 분해해 적재한다. 얇은 이벤트({@code orderId} 만 보유)를
     * 받아, 같은 TX(BEFORE_COMMIT)에서 주문을 재조회해 라인아이템 {@code (productId, quantity)} 브레이크다운을
     * 얻는다(전파 페이로드 조립은 outbox 의 책임). 각 행 key=productId 라 상품 단위 파티션 직렬화가 유지되고,
     * eventId 도 라인아이템마다 별개라 소비자 멱등이 상품별로 분리된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Order order = orderRepository.find(event.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "주문을 찾을 수 없습니다. (orderId: " + event.orderId() + ")"));
        for (OrderItem item : order.getItems()) {
            String eventId = UUID.randomUUID().toString();
            OrderEventMessage message = new OrderEventMessage(
                    eventId, OrderEventType.PRODUCT_SOLD, item.getProductId(), item.getQuantity(),
                    order.getId(), order.getUserId(), event.occurredAt());
            outboxEventJpaRepository.save(OutboxEvent.pending(
                    eventId, String.valueOf(item.getProductId()), KafkaTopicConfig.ORDER_EVENTS, serialize(message)));
        }
    }

    private void enqueueCatalog(CatalogEventType type, Long productId, Long userId, ZonedDateTime occurredAt) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventMessage message = new CatalogEventMessage(eventId, type, productId, userId, occurredAt);
        outboxEventJpaRepository.save(
                OutboxEvent.pending(eventId, String.valueOf(productId), KafkaTopicConfig.CATALOG_EVENTS, serialize(message)));
    }

    private String serialize(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "outbox payload 직렬화에 실패했습니다: " + e.getMessage());
        }
    }
}
