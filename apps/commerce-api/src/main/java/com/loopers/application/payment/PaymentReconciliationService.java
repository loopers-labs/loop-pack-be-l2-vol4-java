package com.loopers.application.payment;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentRequestResult;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentTransactionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(5);
    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(10);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentTransactionWriter paymentTransactionWriter;

    public List<Long> findReconcilablePaymentIds(ZonedDateTime now) {
        return paymentRepository.findPendingRequestedBefore(now.minus(GRACE_PERIOD)).stream()
            .map(PaymentModel::getId)
            .toList();
    }

    public void reconcile(Long paymentId, ZonedDateTime now) {
        PaymentModel payment = paymentRepository.getById(paymentId);
        if (payment.isTerminal()) {
            return;
        }

        PaymentTransactionStatus transactionStatus = paymentGateway.queryTransaction(payment);
        switch (transactionStatus.outcome()) {
            case FOUND -> applyFound(payment, transactionStatus, now);
            case NOT_FOUND -> reRequest(payment);
            case UNKNOWN -> isolateIfExpired(payment, now);
        }
    }

    public PaymentStatus reconcileByOrderId(Long orderId, ZonedDateTime now) {
        PaymentModel payment = paymentRepository.getByOrderId(orderId);
        reconcile(payment.getId(), now);

        return paymentRepository.getById(payment.getId()).getStatus();
    }

    private void applyFound(PaymentModel payment, PaymentTransactionStatus transactionStatus, ZonedDateTime now) {
        if (transactionStatus.isStillProcessing()) {
            isolateIfExpired(payment, now);
            return;
        }

        paymentTransactionWriter.confirm(payment, transactionStatus);
    }

    private void reRequest(PaymentModel payment) {
        PaymentRequestResult requestResult = paymentGateway.requestPayment(payment);
        paymentTransactionWriter.reapplyRequest(payment, requestResult);
    }

    private void isolateIfExpired(PaymentModel payment, ZonedDateTime now) {
        if (payment.getRequestedAt().isAfter(now.minus(STUCK_THRESHOLD))) {
            return;
        }

        paymentTransactionWriter.isolate(payment);
        log.warn("결제 정합성 복구 상한 초과 → STUCK 격리 (orderId={}, paymentId={})", payment.getOrderId(), payment.getId());
    }
}
