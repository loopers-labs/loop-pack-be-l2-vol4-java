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
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
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

    // NOT_SUPPORTED 메서드에서 @Transactional 메서드 호출 — 프록시 경유 필수
    @Lazy
    @Autowired
    private PaymentFacade self;

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

    /** PG 결제 요청 — PG 기존 결제 건 선조회 후 신규 결제 진행. 외부 HTTP 호출이므로 트랜잭션 없음 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String requestPayment(UUID orderId, UUID userId, String cardType, String cardNo) {
        OrderModel order = orderService.getByIdAndUser(orderId, userId);

        try {
            List<PgTransactionResult> existing = pgGateway.findByOrderId(userId.toString(), orderId.toString());
            for (PgTransactionResult tx : existing) {
                switch (tx.status()) {
                    case SUCCESS -> {
                        self.confirm(orderId, tx.transactionKey(), order.getPgAmount());
                        throw new CoreException(ErrorType.BAD_REQUEST, "이미 결제 완료된 주문입니다.");
                    }
                    case PENDING -> throw new CoreException(ErrorType.BAD_REQUEST, "결제가 이미 진행 중입니다.");
                    case FAILED -> self.fail(orderId, tx.transactionKey(), 0L);
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
                        case SUCCESS -> self.confirm(order.getId(), tx.transactionKey(), order.getPgAmount());
                        case FAILED -> self.fail(order.getId(), tx.transactionKey(), 0L);
                        case PENDING -> {} // 아직 처리 중
                    }
                }
            } catch (Exception ignored) {
                // 개별 주문 실패 시 다음 주문 계속
            }
        }
    }
}
