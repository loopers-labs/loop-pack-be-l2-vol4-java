package com.loopers.application.payment;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 결과 확정의 공유 수렴 지점. 콜백(push)·폴링/복구(pull) 어느 경로로 결과가 들어오든 모두 이 메서드로 수렴해
 * 같은 도메인 전이를 일으킨다(두 수신 경로가 어긋나지 않도록). 도메인 메서드가 멱등이라 중복·순서뒤바뀜 수신에 안전하다.
 */
@Component
@RequiredArgsConstructor
public class PaymentConfirmation {

    private final PaymentService paymentService;
    private final OrderService orderService;

    @Transactional
    public void confirm(String transactionKey, PaymentStatus result, String reason) {
        switch (result) {
            case SUCCESS -> {
                PaymentModel payment = paymentService.markSuccess(transactionKey, reason);
                orderService.markPaid(payment.getOrderId());
            }
            case FAILED -> {
                PaymentModel payment = paymentService.markFailed(transactionKey, reason);
                orderService.markPaymentFailed(payment.getOrderId());
            }
            case PENDING -> {
                // 아직 미확정 결과 → 확정하지 않는다 (no-op)
            }
        }
    }
}
