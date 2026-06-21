package com.loopers.infrastructure.payment.scheduler;

import com.loopers.application.payment.payment.PaymentProcessResult;
import com.loopers.application.payment.payment.PaymentWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "commerce.workers.payment.enabled", havingValue = "true")
public class PaymentWorkerScheduler {

    private final PaymentWorker paymentWorker;

    @Scheduled(
        initialDelayString = "${commerce.workers.payment.initial-delay-ms:5000}",
        fixedDelayString = "${commerce.workers.payment.fixed-delay-ms:5000}"
    )
    public void processRequestedPayments() {
        List<PaymentProcessResult> results = paymentWorker.processRequestedPayments();
        log.info("payment worker processed requested payments. count={}", results.size());
    }
}
