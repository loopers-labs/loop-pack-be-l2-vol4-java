package com.loopers.payment.application;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayOrderTransactions;
import com.loopers.payment.domain.PaymentGatewayQueryResult;
import com.loopers.payment.domain.PaymentGatewayTransaction;
import com.loopers.payment.domain.PaymentGatewayTransactionDetail;
import com.loopers.payment.domain.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Component
public class PaymentRecoveryFacade {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final PaymentRecoveryResultHandler resultHandler;
    private final PaymentRecoveryProperties properties;

    public int recoverDuePayments() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Payment> payments = paymentService.findRecoverablePayments(
            now,
            now.minusSeconds(properties.requestingTimeoutSeconds()),
            now.minusSeconds(properties.pendingTimeoutSeconds()),
            properties.chunkSize()
        );
        payments.forEach(payment -> recoverSafely(payment, now));
        return payments.size();
    }

    private void recoverSafely(Payment payment, ZonedDateTime now) {
        try {
            recover(payment, now);
        } catch (RuntimeException e) {
            log.warn("Failed to recover payment. paymentId={}, orderId={}", payment.getId(), payment.getOrderId(), e);
        }
    }

    private void recover(Payment payment, ZonedDateTime now) {
        if (payment.getPgTransactionKey() == null) {
            recoverByOrderId(payment, now);
            return;
        }
        recoverByTransactionKey(payment, payment.getPgTransactionKey(), now);
    }

    private void recoverByOrderId(Payment payment, ZonedDateTime now) {
        PaymentGatewayQueryResult<PaymentGatewayOrderTransactions> result = paymentGateway.getTransactionsByOrderId(
            payment.getUserId(),
            payment.getOrderId()
        );

        if (!result.isFound()) {
            handleQueryMiss(payment, result.failureReason(), result.reason(), now);
            return;
        }

        PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> transaction = findMatchingTransaction(
            payment,
            result.data().transactions()
        );
        if (!transaction.isFound()) {
            handleQueryMiss(payment, transaction.failureReason(), transaction.reason(), now);
            return;
        }

        resultHandler.applyTransaction(payment.getId(), transaction.data(), now, nextRecoveryAt(now));
    }

    private void recoverByTransactionKey(Payment payment, String transactionKey, ZonedDateTime now) {
        PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> result = paymentGateway.getTransaction(
            payment.getUserId(),
            transactionKey
        );

        if (!result.isFound()) {
            handleQueryMiss(payment, result.failureReason(), result.reason(), now);
            return;
        }

        resultHandler.applyTransaction(payment.getId(), result.data(), now, nextRecoveryAt(now));
    }

    private void handleQueryMiss(
        Payment payment,
        PaymentFailureReason failureReason,
        String reason,
        ZonedDateTime now
    ) {
        if (failureReason == PaymentFailureReason.PG_TRANSACTION_NOT_FOUND
            && payment.canConfirmRequestFailure()
            && payment.isRecoveryGraceExpired(now, Duration.ofSeconds(properties.notFoundGraceSeconds()))
        ) {
            resultHandler.markRequestFailed(payment.getId(), failureReason, reason, now);
            return;
        }
        resultHandler.scheduleRecovery(payment.getId(), reason, nextRecoveryAt(now));
    }

    private PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> findMatchingTransaction(
        Payment payment,
        List<PaymentGatewayTransaction> transactions
    ) {
        PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> lastQueryMiss = null;

        for (PaymentGatewayTransaction transaction : transactions) {
            PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> result = paymentGateway.getTransaction(
                payment.getUserId(),
                transaction.transactionKey()
            );
            if (!result.isFound()) {
                if (result.failureReason() != PaymentFailureReason.PG_TRANSACTION_NOT_FOUND) {
                    lastQueryMiss = result;
                }
                continue;
            }
            PaymentGatewayTransactionDetail transactionDetail = result.data();
            if (payment.isSamePayment(
                transactionDetail.orderId(),
                transactionDetail.amount(),
                transactionDetail.cardType()
            )) {
                return result;
            }
        }

        if (lastQueryMiss != null) {
            return lastQueryMiss;
        }
        return PaymentGatewayQueryResult.notFound("matching PG transaction not found");
    }

    private ZonedDateTime nextRecoveryAt(ZonedDateTime now) {
        return now.plusSeconds(properties.retryDelaySeconds());
    }
}
