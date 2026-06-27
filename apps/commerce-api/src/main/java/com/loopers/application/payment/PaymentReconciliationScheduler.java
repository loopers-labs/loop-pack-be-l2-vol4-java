package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 콜백 유실에 대비한 정합성 보정(reconciliation).
 * 유예 시간이 지나도록 PENDING 인 결제를 PG에 직접 조회해 PaymentFacade.confirm 으로 확정한다.
 * confirm 이 멱등이라 뒤늦게 도착한 콜백과 겹쳐도 안전하다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentReconciliationScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentFacade paymentFacade;

    @Value("${payment.reconciliation.grace-period-seconds}")
    private long gracePeriodSeconds;

    @Scheduled(fixedDelayString = "${payment.reconciliation.interval-ms}")
    public void reconcile() {
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(gracePeriodSeconds);
        List<Payment> stalePayments = paymentRepository.findStalePending(threshold);
        for (Payment payment : stalePayments) {
            try {
                PaymentGateway.PaymentResult result = paymentGateway.getTransaction(
                    String.valueOf(payment.getUserId()), payment.getTransactionKey());
                paymentFacade.confirm(payment.getTransactionKey(), result.status(), result.reason());
            } catch (Exception e) {
                log.warn("결제 정합성 보정 실패 (transactionKey={}): {}", payment.getTransactionKey(), e.getMessage());
            }
        }
    }
}
