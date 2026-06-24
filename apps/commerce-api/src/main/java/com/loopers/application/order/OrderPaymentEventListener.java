package com.loopers.application.order;

import com.loopers.domain.payment.PaymentCompleted;
import com.loopers.domain.payment.PaymentFailed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 도메인 이벤트를 주문 반영으로 잇는 얇은 배선.
 * AFTER_COMMIT으로 결제 확정 트랜잭션이 커밋된 뒤에만 반영한다.
 * (다음 주 Kafka 전환 시 이 클래스만 @KafkaListener로 교체하고, OrderPaymentResultHandler는 그대로 둔다.)
 */
@RequiredArgsConstructor
@Component
public class OrderPaymentEventListener {

    private final OrderPaymentResultHandler handler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentCompleted event) {
        handler.onPaid(event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentFailed event) {
        handler.onFailed(event.orderId());
    }
}
