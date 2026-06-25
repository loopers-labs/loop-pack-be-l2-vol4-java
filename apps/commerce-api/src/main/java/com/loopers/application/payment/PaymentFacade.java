package com.loopers.application.payment;

import com.loopers.config.PgProperties;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.pg.PgGateway;
import com.loopers.domain.pg.PgTransactionResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentSyncComponent paymentSyncComponent;
    private final PgGateway pgGateway;
    private final PgProperties pgProperties;

    /** 결제 확정 — 콜백 수신 시 호출 */
    public PaymentInfo confirm(UUID orderId, String pgTransactionId, Long amount) {
        return paymentSyncComponent.confirm(orderId, pgTransactionId, amount);
    }

    /** 결제 실패 — 콜백 수신 시 호출 */
    public PaymentInfo fail(UUID orderId, String pgTransactionId, Long amount) {
        return paymentSyncComponent.fail(orderId, pgTransactionId, amount);
    }

    /** PG 결제 요청 — 기존 결제 건 선조회 후 중복 차단, 신규 결제 진행. 외부 HTTP 호출이므로 트랜잭션 없음 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String requestPayment(UUID orderId, UUID userId, String cardType, String cardNo) {
        OrderModel order = orderService.getByIdAndUser(orderId, userId);

        try {
            List<PgTransactionResult> existing = pgGateway.findByOrderId(userId.toString(), orderId.toString());
            for (PgTransactionResult tx : existing) {
                switch (tx.status()) {
                    case SUCCESS -> {
                        paymentSyncComponent.confirm(orderId, tx.transactionKey(), order.getPgAmount());
                        throw new CoreException(ErrorType.BAD_REQUEST, "이미 결제 완료된 주문입니다.");
                    }
                    case PENDING -> throw new CoreException(ErrorType.BAD_REQUEST, "결제가 이미 진행 중입니다.");
                    case FAILED -> paymentSyncComponent.fail(orderId, tx.transactionKey(), 0L);
                }
            }
        } catch (CoreException e) {
            throw e;
        } catch (Exception ignored) {
            // PG 조회 실패 시 신규 결제 진행 (fail-open)
        }

        PgTransactionResult result = pgGateway.request(
            userId.toString(),
            orderId.toString(),
            cardType,
            cardNo,
            order.getPgAmount(),
            pgProperties.getCallbackUrl()
        );
        return result.transactionKey();
    }

    /** 스케줄러용 PENDING 주문 PG 상태 동기화. 외부 HTTP 호출이므로 트랜잭션 없음 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void syncPendingOrders(ZonedDateTime before) {
        List<OrderModel> orders = orderService.findPendingOlderThan(before);
        for (OrderModel order : orders) {
            try {
                List<PgTransactionResult> transactions = pgGateway.findByOrderId(
                    order.getUserId().toString(), order.getId().toString()
                );
                for (PgTransactionResult tx : transactions) {
                    switch (tx.status()) {
                        case SUCCESS -> paymentSyncComponent.confirm(order.getId(), tx.transactionKey(), order.getPgAmount());
                        case FAILED -> paymentSyncComponent.fail(order.getId(), tx.transactionKey(), 0L);
                        case PENDING -> {}
                    }
                }
            } catch (Exception ignored) {
                // 개별 주문 실패 시 다음 주문 계속
            }
        }
    }
}
