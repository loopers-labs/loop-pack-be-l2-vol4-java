package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 방치된 PENDING 주문 만료 스케줄러.
 *
 * <p><strong>왜 필요한가</strong>: 주문 생성(TX1)과 결제 확정(confirm)이 분리되어 있어,
 * 유저가 PG 결제창에서 이탈하면 재고/쿠폰을 점유한 PENDING 주문이 영구히 남는다.
 * 이 케이스는 <strong>웹훅으로 덮을 수 없다</strong> — 유저가 인증을 시작도 안 했다면
 * PG 에는 이 거래의 기록 자체가 없어서 통지할 이벤트가 존재하지 않는다.
 * 방치된 주문은 우리만 알 수 있으므로, 우리 쪽 스케줄러가 정리해야 한다.
 *
 * <p><strong>만료 전 PG 조회로 이중 확인</strong>: 승인 타임아웃처럼 "실제로는 결제됐는데
 * 우리만 모르는" 주문을 무턱대고 보상(재고 복구)하면 "돈은 나갔는데 주문은 실패" 사고가 된다.
 * 만료 처리 전 PG 결제 조회로 진실을 확인해, 결제된 주문은 COMPLETED 로 확정하고
 * 결제 안 된 주문만 보상한다. (실서비스의 대사 개념을 단순화한 것)
 *
 * <p><strong>동시성</strong>: 스케줄러의 만료 처리와 뒤늦게 돌아온 유저의 confirm 이 경합해도,
 * 주문 상태 머신 가드(complete/fail 모두 PENDING 에서만 전이 허용)가 한쪽만 통과시킨다.
 *
 * <p><strong>알려진 한계</strong>: {@code @Scheduled} 는 다중 인스턴스 환경에서 중복 실행된다.
 * 실서비스에서는 ShedLock 같은 분산 락이나 Spring Batch + 외부 트리거로 단일 실행을 보장해야
 * 하지만, 과제 범위(단일 인스턴스)에서는 다루지 않는다. (중복 실행되더라도 상태 머신 가드 덕에
 * 이중 보상은 일어나지 않는다 — 한쪽은 상태 전이에서 거부된다)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PendingOrderExpirationScheduler {

    /** PG 인증 유효시간을 고려한 만료 기준 (분). 이 시간 안에 confirm 이 없으면 이탈로 간주. */
    private static final int EXPIRE_AFTER_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final OrderTransactionService orderTransactionService;

    @Scheduled(fixedDelay = 60_000)
    public void expireStalePendingOrders() {
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(EXPIRE_AFTER_MINUTES);
        List<OrderModel> staleOrders = orderRepository.findByStatusAndOrderedAtBefore(OrderStatus.PENDING, cutoff);
        for (OrderModel order : staleOrders) {
            try {
                resolve(order);
            } catch (Exception e) {
                // 한 건의 실패가 나머지 만료 처리를 막지 않도록 격리
                log.warn("PENDING 주문 만료 처리 실패 — orderId: {}", order.getId(), e);
            }
        }
    }

    private void resolve(OrderModel order) {
        // 만료 전 PG 조회 — "결제는 됐는데 confirm 만 유실된" 주문을 보상해버리는 사고 방지
        boolean paidAtPg = paymentGateway.inquire(order.getId())
            .map(PaymentResult::isSuccess)
            .orElse(false);

        if (paidAtPg) {
            orderTransactionService.completePayment(order.getId());
            log.info("PG 조회 결과 결제 완료 확인 — 주문 확정 처리. orderId: {}", order.getId());
        } else {
            orderTransactionService.failPaymentAndRelease(order.getId());
            log.info("미결제 PENDING 주문 만료 — 재고/쿠폰 복구. orderId: {}", order.getId());
        }
    }
}
