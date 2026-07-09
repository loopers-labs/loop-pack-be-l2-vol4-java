package com.loopers.application.order;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.order.OrderPaidEvent;
import com.loopers.infrastructure.outbox.EventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link OrderPaidEvent}를 결제 확정 트랜잭션 <b>커밋 직전(BEFORE_COMMIT)</b>에 outbox로 적재한다.
 * 도메인(OrderService)이 Kafka/Outbox를 모르도록 시스템 간 전파 관심사를 분리한 어댑터.
 *
 * <p>{@code markPaid}의 상태 전이·outbox INSERT를 같은 트랜잭션으로 묶어, 결제 확정이 롤백되면 이벤트도
 * 사라지고 커밋되면 반드시 함께 남도록 한다(At Least Once). 실제 Kafka 전송은 {@code OutboxRelay}가 담당.
 *
 * <p>{@code key=orderId}: 같은 주문 이벤트의 파티션 단위 순서 보장. 판매량은 가산이므로 version은 0.
 */
@Component
@RequiredArgsConstructor
public class OrderEventOutboxListener {

    private final OutboxAppender outboxAppender;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(OrderPaidEvent event) {
        outboxAppender.append(
                "order",
                event.orderId(),
                "ORDER_PAID",
                EventTopics.ORDER_EVENTS,
                String.valueOf(event.orderId()),
                event,
                0L
        );
    }
}
