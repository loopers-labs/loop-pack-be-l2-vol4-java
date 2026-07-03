package com.loopers.outbox.application;

import com.loopers.order.application.event.OrderPaidEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 하이브리드 모드의 즉시 발행. 주문이 커밋된 직후(AFTER_COMMIT) 폴 주기를 기다리지 않고 릴레이를 한 번 돌린다.
 * - 평상시 발행 지연을 폴 간격 → 거의 0 으로 줄인다.
 * - 즉시 발행이 실패해도 PENDING 으로 남으므로 스케줄 폴러가 안전망으로 재시도한다.
 * POLLING_ONLY 모드에서는 아무 일도 하지 않는다(순수 폴러).
 */
@Component
public class OutboxImmediatePublishListener {

    private final OutboxEventRelay outboxEventRelay;
    private final boolean hybrid;

    public OutboxImmediatePublishListener(OutboxEventRelay outboxEventRelay,
                                          @Value("${outbox.relay.mode:POLLING_ONLY}") String mode) {
        this.outboxEventRelay = outboxEventRelay;
        this.hybrid = "HYBRID".equalsIgnoreCase(mode);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        if (hybrid) {
            outboxEventRelay.relay();
        }
    }
}
