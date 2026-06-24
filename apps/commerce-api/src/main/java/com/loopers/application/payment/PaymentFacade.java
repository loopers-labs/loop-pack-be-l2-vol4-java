package com.loopers.application.payment;

import com.loopers.config.PgProperties;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStockService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.pg.PgGateway;
import com.loopers.domain.pg.PgTransactionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Component
@Transactional
public class PaymentFacade {

    private final OrderService orderService;
    private final OrderStockService orderStockService;
    private final PaymentService paymentService;
    private final UserCouponService userCouponService;
    private final PgGateway pgGateway;
    private final PgProperties pgProperties;

    /** 결제 확정 — 재고+주문 확정(금액/상태 검증 포함) + 결제 저장 (멱등). 주문 행 비관적 락으로 전이 직렬화 */
    public PaymentInfo confirm(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.getForUpdate(orderId);
        orderStockService.confirmOrder(order, amount);
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.SUCCESS, amount)
        );
        return PaymentInfo.from(payment);
    }

    /** PG 결제 요청 — 주문 조회 후 pg-simulator 호출, transactionKey 반환. 외부 HTTP 호출이므로 트랜잭션 없음 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String requestPayment(UUID orderId, UUID userId, String cardType, String cardNo) {
        OrderModel order = orderService.getByIdAndUser(orderId, userId);
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

    /** 결제 실패 — PENDING이면 재고 해제 + 쿠폰 복구 + 주문 실패 (멱등) + 결제 저장 (멱등). 주문 행 비관적 락으로 전이 직렬화 */
    public PaymentInfo fail(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.getForUpdate(orderId);
        boolean failed = orderStockService.failOrder(order);
        if (failed) {
            userCouponService.releaseByOrderId(orderId); // 실제 FAILED 전이됐을 때만 쿠폰 복구
        }
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.FAILED, amount)
        );
        return PaymentInfo.from(payment);
    }
}
