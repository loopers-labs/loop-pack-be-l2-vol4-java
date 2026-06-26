package com.loopers.application.order;

import com.loopers.domain.payment.PaymentCompleted;
import com.loopers.domain.payment.PaymentFailed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka 전환 시 이 클래스만 @KafkaListener로 교체하고 핸들러는 그대로 둔다. */
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
