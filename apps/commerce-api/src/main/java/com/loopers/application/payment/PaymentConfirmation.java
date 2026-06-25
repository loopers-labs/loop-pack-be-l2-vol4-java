package com.loopers.application.payment;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentGatewayTransaction;
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
        if (result == PaymentStatus.PENDING) {
            // 아직 미확정 결과 → 조회조차 하지 않고 확정하지 않는다 (no-op)
            return;
        }
        converge(paymentService.getByTransactionKey(transactionKey), result, reason);
    }

    /**
     * key 를 못 받은 결제(keyless)를 orderId 로 PG 에 되물어 얻은 대표 결과로 수렴시킨다.
     * 응답에 transactionKey 가 있으면 결제에 흡수해 다음 스캔부터 keyed 경로로 승격되게 하고,
     * 비어 있으면(PG 미접수) 흡수 없이 FAILED 로 정정한다. 전이는 confirm 과 같은 converge 로 수렴한다.
     */
    @Transactional
    public void confirmByOrder(Long paymentId, PaymentGatewayTransaction outcome) {
        PaymentModel payment = paymentService.getById(paymentId);
        payment.assignTransactionKey(outcome.transactionKey());
        converge(payment, outcome.status(), outcome.reason());
    }

    /**
     * 결제·주문 상태 전이의 공유 수렴 코어. 콜백·keyed 복구(confirm)와 keyless 복구(confirmByOrder)가
     * 결제를 확보하는 방법만 다를 뿐, 확보한 뒤의 전이는 모두 이 메서드로 모인다.
     */
    private void converge(PaymentModel payment, PaymentStatus result, String reason) {
        switch (result) {
            case SUCCESS -> {
                payment.markSuccess(reason);
                orderService.markPaid(payment.getOrderId());
            }
            case FAILED -> {
                payment.markFailed(reason);
                orderService.markPaymentFailed(payment.getOrderId());
            }
            case PENDING -> {
                // 아직 미확정 → 전이 없음 (keyless 경유 시 흡수한 key 만 아래 save 로 영속될 수 있음)
            }
        }
        paymentService.save(payment);
    }
}
