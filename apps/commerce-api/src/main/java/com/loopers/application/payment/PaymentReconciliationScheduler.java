package com.loopers.application.payment;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.support.utils.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final long FIXED_DELAY_MILLIS = 10_000L;

    private final PaymentReconciliationService paymentReconciliationService;
    private final DateTimeUtil dateTimeUtil;

    @Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
    public void reconcilePendingPayments() {
        ZonedDateTime now = dateTimeUtil.now();
        List<Long> paymentIds = paymentReconciliationService.findReconcilablePaymentIds(now);

        for (Long paymentId : paymentIds) {
            try {
                paymentReconciliationService.reconcile(paymentId, now);
            } catch (Exception e) {
                log.warn("결제 정합성 복구 실패 (paymentId={})", paymentId, e);
            }
        }
    }
}
