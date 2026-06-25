package com.loopers.payment.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderService;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGatewayTransactionDetail;
import com.loopers.payment.domain.PaymentService;
import com.loopers.payment.domain.PgPaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class PaymentRecoveryResultHandler {

    private final PaymentService paymentService;
    private final OrderService orderService;

    @Transactional
    public void applyTransaction(
        Long paymentId,
        PaymentGatewayTransactionDetail transaction,
        ZonedDateTime completedAt,
        ZonedDateTime nextRecoveryAt
    ) {
        Payment payment = paymentService.getPayment(paymentId);
        payment.validateSamePayment(transaction.orderId(), transaction.amount(), transaction.cardType());

        if (transaction.status() == PgPaymentStatus.PENDING) {
            payment.markPending(transaction.transactionKey());
            payment.scheduleRecovery(nextRecoveryAt, transaction.reason());
            return;
        }

        Order order = orderService.getOrder(payment.getOrderId());
        if (transaction.status() == PgPaymentStatus.SUCCESS) {
            payment.markSucceeded(transaction.transactionKey(), transaction.reason(), completedAt);
            order.completePayment();
            return;
        }

        payment.markFailed(
            transaction.transactionKey(),
            PgPaymentFailureReason.resolve(transaction.reason()),
            transaction.reason(),
            completedAt
        );
        order.failPayment();
    }

    @Transactional
    public void markPending(Long paymentId, String transactionKey, String reason, ZonedDateTime nextRecoveryAt) {
        Payment payment = paymentService.getPayment(paymentId);
        payment.markPending(transactionKey);
        payment.scheduleRecovery(nextRecoveryAt, reason);
    }

    @Transactional
    public void scheduleRecovery(Long paymentId, String reason, ZonedDateTime nextRecoveryAt) {
        Payment payment = paymentService.getPayment(paymentId);
        payment.scheduleRecovery(nextRecoveryAt, reason);
    }

    @Transactional
    public void markRequestFailed(
        Long paymentId,
        PaymentFailureReason failureReason,
        String reason,
        ZonedDateTime completedAt
    ) {
        Payment payment = paymentService.getPayment(paymentId);
        payment.markRequestFailed(failureReason, reason, completedAt);
    }
}
