package com.loopers.application.payment;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 1건을 transactionKey로 잠그고 최종 상태(SUCCESS/FAILED)를 반영하면서 주문 확정까지
 * <b>한 트랜잭션</b>으로 처리하는 "정확히 한 번" 확정 단위. PG 콜백(§3.4)과 Reconcile(§3.5)이 공유한다.
 * <p>
 * 별도 빈으로 분리한 이유: 이 메서드는 {@code findByTransactionKeyForUpdate}의 비관락을 mark·cascade까지
 * 유지해야 한다. Facade가 자기 자신의 {@code @Transactional} 메서드를 직접 호출하면 Spring 프록시를 거치지
 * 않아 트랜잭션이 시작되지 않는다(self-invocation). 콜백·Reconcile 두 경로가 모두 프록시를 통해 호출하도록
 * 확정 로직을 이 빈으로 빼냈다.
 */
@RequiredArgsConstructor
@Component
public class PaymentConfirmer {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    /**
     * 결제를 비관락으로 잠그고 결과를 반영한다. 멱등: 이미 확정(SUCCESS/FAILED)된 결제는 재반영 없이
     * 현재 상태를 반환한다. 동시 확정(콜백/Reconcile)은 락으로 직렬화돼 상태 전이가 정확히 한 번만 일어난다.
     *
     * @return 반영 후(또는 이미 확정돼 있던) 결제. 호출부가 최종 상태로 결과를 집계할 수 있다.
     */
    @Transactional
    public PaymentModel confirm(String transactionKey, PaymentStatus resultStatus, String reason) {
        PaymentModel payment = paymentRepository.findByTransactionKeyForUpdate(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));

        // 이미 확정된 결제 → 중복 확정(콜백/Reconcile 경합). 재반영 없이 현재 상태 반환(멱등).
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }

        if (resultStatus == PaymentStatus.SUCCESS) {
            payment.markSuccess();
            PaymentModel saved = paymentRepository.save(payment);
            confirmOrder(payment.getOrderId(), true, null);
            return saved;
        }
        if (resultStatus == PaymentStatus.FAILED) {
            payment.markFailed(reason);
            PaymentModel saved = paymentRepository.save(payment);
            confirmOrder(payment.getOrderId(), false, reason);
            return saved;
        }
        // PENDING 등 미확정 상태로는 결제를 확정하지 않는다(그대로 둠).
        return payment;
    }

    /** 주문 확정 연계. 이미 다른 경로(콜백/Reconcile)로 확정된 주문은 CONFLICT를 멱등 skip 한다. */
    private void confirmOrder(Long orderId, boolean success, String reason) {
        try {
            if (success) {
                orderService.markPaid(orderId);
            } else {
                orderService.markFailed(orderId, reason);
            }
        } catch (CoreException e) {
            if (e.getErrorType() != ErrorType.CONFLICT) {
                throw e;
            }
            // 이미 확정된 주문 — 멱등 skip
        }
    }
}
