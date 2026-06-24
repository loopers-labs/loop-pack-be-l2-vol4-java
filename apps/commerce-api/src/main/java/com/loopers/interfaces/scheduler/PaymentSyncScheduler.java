package com.loopers.interfaces.scheduler;

import com.loopers.application.payment.PaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * 5분 초과 PENDING 주문 PG 상태 동기화 스케줄러.
 * 콜백 미수신 시 PG 직접 조회하여 SUCCESS/FAILED 반영.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentSyncScheduler {

    private static final int SYNC_THRESHOLD_MINUTES = 5;

    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 5 * 60_000)
    public void syncPendingOrders() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(SYNC_THRESHOLD_MINUTES);
        log.debug("PENDING 결제 동기화 실행 — threshold: {}", threshold);
        paymentFacade.syncPendingOrders(threshold);
    }
}
