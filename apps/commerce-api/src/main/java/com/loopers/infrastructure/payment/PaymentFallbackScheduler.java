package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFallbackScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentFacade paymentFacade;

    // 30분마다 실행하여, 생성된 지 30분이 지난 READY 결제 건들을 자동 보정
    @Scheduled(cron = "0 */30 * * * *")
    public void run() {
        log.info("Starting Payment Fallback Scheduler...");
        
        ZonedDateTime thresholdTime = ZonedDateTime.now().minusMinutes(30);
        List<PaymentModel> oldReadyPayments = paymentRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.READY, thresholdTime);
        
        log.info("Found {} old READY payments to process fallback", oldReadyPayments.size());
        
        for (PaymentModel payment : oldReadyPayments) {
            try {
                log.info("Processing fallback for payment id: {}", payment.getId());
                paymentFacade.retryOrCompensatePayment(payment.getId(), true);
            } catch (Exception e) {
                log.error("Failed to run fallback correction for payment id: {}", payment.getId(), e);
            }
        }
        
        log.info("Payment Fallback Scheduler finished.");
    }
}
