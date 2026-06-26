package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/** PG 호출이 끼므로 Facade에 트랜잭션을 두지 않는다 — PG 호출이 락을 점유하지 않게 한다. */
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PaymentInfo requestPayment(Long userId, Long orderId, CardType cardType, String cardNo) {
        OrderModel order = orderService.getById(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        long amount = order.getFinalAmount().value();

        PaymentModel payment = paymentService.createPending(orderId, userId, cardType, Money.of(amount));

        // 이미 거래키를 받은 PENDING(이전 시도 성공분)이면 PG를 다시 때리지 않는다 — 이중 접수 방지.
        if (payment.getTransactionKey() == null) {
            GatewayResult result = paymentGateway.requestPayment(
                new GatewayCommand(orderId, userId, cardType, cardNo, amount));
            if (result.accepted()) {
                paymentService.assignTransactionKey(orderId, result.transactionKey());
            }
        }

        return PaymentInfo.from(paymentService.getByOrderId(orderId));
    }

    /** 콜백 본문은 위·변조될 수 있어 신뢰하지 않는다 — 트리거로만 쓰고 실제 상태는 PG 재조회로 확정한다. */
    public void handleCallback(String transactionKey) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        try {
            paymentGateway.queryStatus(transactionKey, payment.getUserId())
                .ifPresent(s -> paymentService.confirmFromGatewayStatus(transactionKey, s.status(), s.reason()));
        } catch (ObjectOptimisticLockingFailureException alreadyConfirmed) {
            // 콜백·복구가 동시에 확정 — 승자가 이미 반영했으므로 no-op
        }
    }

    public PaymentInfo getStatus(Long userId, Long orderId) {
        PaymentModel payment = paymentService.getByOrderId(orderId);
        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다.");
        }
        return PaymentInfo.from(payment);
    }
}
