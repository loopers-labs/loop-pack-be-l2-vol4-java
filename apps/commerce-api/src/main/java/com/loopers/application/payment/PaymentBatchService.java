package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.infrastructure.pg.PgClientWrapper;
import com.loopers.infrastructure.pg.PgResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentBatchService {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PgClientWrapper pgClient;
    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 30000)
    public void recoverPendingPayments() {
        recover(ZonedDateTime.now().minusSeconds(30));
    }

    void recover(ZonedDateTime threshold) {
        List<PaymentModel> pendingPayments = paymentService.findPendingBefore(threshold);
        for (PaymentModel payment : pendingPayments) {
            tryRecover(payment);
        }
    }

    private void tryRecover(PaymentModel payment) {
        Long orderId = payment.getOrderId();
        String pgOrderId = String.format("%012d", orderId);

        try {
            OrderModel order = orderService.getById(orderId);
            String userId = String.valueOf(order.getMemberId());

            PgResponse.OrderResponse pgResult = pgClient.getTransactionsByOrderId(userId, pgOrderId);
            if (pgResult.transactions().isEmpty()) {
                log.info("[orderId={}] PG 거래 내역 없음 - 다음 배치에서 재시도", orderId);
                return;
            }
            PgResponse.TransactionResponse latest = pgResult.transactions().get(0);

            switch (latest.status()) {
                case "SUCCESS" -> {
                    if (order.getStatus() == OrderStatus.CANCELLED) {
                        log.warn("[orderId={}] PG SUCCESS이나 주문이 이미 취소됨 - CONFLICT 마킹", orderId);
                        paymentService.markConflictByOrderId(orderId);
                    } else {
                        paymentFacade.recoverAsSuccess(orderId, latest.transactionKey());
                    }
                }
                case "FAILED" -> {
                    paymentFacade.recoverAsFailure(orderId, latest.reason());
                }
                default -> log.info("[orderId={}] PG PENDING - 다음 배치에서 재시도", orderId);
            }
        } catch (FeignException e) {
            if (e.status() == 404) {
                log.warn("[orderId={}] PG 결제 기록 없음 - 실패 처리", orderId);
                paymentFacade.recoverAsFailure(orderId, "PG 결제 기록 없음");
            } else {
                log.error("[orderId={}] PG 조회 중 오류 발생 - 다음 배치에서 재시도", orderId, e);
            }
        } catch (Exception e) {
            log.error("[orderId={}] 배치 복구 중 예상치 못한 오류 발생", orderId, e);
        }
    }
}
