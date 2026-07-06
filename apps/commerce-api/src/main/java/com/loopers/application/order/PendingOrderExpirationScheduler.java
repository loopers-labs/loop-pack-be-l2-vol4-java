package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 미완결 PENDING 주문 만료 스케줄러.
 *
 * <p>무점유 견적(PENDING)은 유저가 결제창에서 이탈한 빈 껍데기다. 점유한 자원이 없으므로
 * 상태만 FAILED 로 닫는다. PG 승인은 점유(bind) 후에만 호출되므로 PENDING 주문엔 결제가
 * 존재할 수 없다 — PG 조회도 불필요하다.
 *
 * <p><strong>PAYMENT_IN_PROGRESS 보정은 여기서 하지 않는다</strong>: 점유 완료 후 결과 미상
 * 건의 PG 대사·확정/보상은 {@link com.loopers.application.payment.PgReconcileScheduler} 가
 * 단일 책임으로 담당한다 (pg-simulator 게이트웨이로 일원화). 두 스케줄러가 서로 다른 게이트웨이로
 * 같은 주문을 판정해 충돌하는 것을 막기 위함이다.
 *
 * <p><strong>동시성</strong>: 보상/확정 모두 주문 행 비관적 락 + 상태 가드로 직렬화된다.
 *
 * <p><strong>알려진 한계</strong>: {@code @Scheduled} 는 다중 인스턴스 환경에서 중복 실행된다.
 * 실서비스에서는 ShedLock 같은 분산 락이 필요하지만 과제 범위(단일 인스턴스)에서는 다루지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PendingOrderExpirationScheduler {

    /** 무점유 견적(PENDING)의 만료 기준 — 주문 생성 후 이 시간 내 confirm 이 없으면 이탈로 간주. */
    private static final int PENDING_EXPIRE_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderTransactionService orderTransactionService;

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleOrders() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        expireAbandonedPendingOrders(now);
    }

    /** 무점유 견적 정리 — 복구할 자원이 없으므로 상태만 닫는다. */
    private void expireAbandonedPendingOrders(ZonedDateTime now) {
        List<OrderModel> abandoned = orderRepository.findByStatusAndOrderedAtBefore(
            OrderStatus.PENDING, now.minusMinutes(PENDING_EXPIRE_MINUTES));
        for (OrderModel order : abandoned) {
            try {
                orderTransactionService.markOrderFailed(order.getId());
                log.info("이탈 PENDING 주문 만료 처리. orderId: {}", order.getId());
            } catch (Exception e) {
                log.warn("PENDING 주문 만료 처리 실패 — orderId: {}", order.getId(), e);
            }
        }
    }
}
