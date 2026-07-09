package com.loopers.application.product;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.infrastructure.outbox.EventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link ProductViewedEvent}를 조회 기록 트랜잭션 <b>커밋 직전(BEFORE_COMMIT)</b>에 outbox로 적재한다.
 * (LIKE_CHANGED/ORDER_PAID와 동일한 Transactional Outbox 패턴 — 실제 Kafka 전송은 OutboxRelay가 담당)
 *
 * <p>{@code key=productId}: 같은 상품 이벤트가 같은 파티션으로 가서 소비자가 상품별로 집계한다.
 * 조회수는 가산이라 version은 0.
 */
@Component
@RequiredArgsConstructor
public class ProductViewOutboxListener {

    private final OutboxAppender outboxAppender;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(ProductViewedEvent event) {
        outboxAppender.append(
                "product",
                event.productId(),
                "PRODUCT_VIEWED",
                EventTopics.CATALOG_EVENTS,
                String.valueOf(event.productId()),
                event,
                0L
        );
    }
}
