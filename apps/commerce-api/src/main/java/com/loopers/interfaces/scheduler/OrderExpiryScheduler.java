package com.loopers.interfaces.scheduler;

import com.loopers.application.order.OrderFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * 15분 초과 PENDING 주문 자동 만료 스케줄러.
 * 매 1분마다 실행 — 생성 후 15분이 지난 PENDING 주문을 FAILED 처리하고 재고를 해제한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderExpiryScheduler {

    private static final int PENDING_EXPIRY_MINUTES = 15;

    private final OrderFacade orderFacade;

    @Scheduled(fixedDelay = 60_000)
    public void expirePendingOrders() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES);
        log.debug("PENDING 주문 만료 처리 실행 — threshold: {}", threshold);
        orderFacade.expirePendingOrders(threshold);
    }
}
