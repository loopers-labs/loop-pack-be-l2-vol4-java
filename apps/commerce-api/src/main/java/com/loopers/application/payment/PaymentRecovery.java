package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayTransaction;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 콜백이 유실돼 일정 시간 이상 PENDING 으로 멈춘 결제를, PG 에 직접 상태를 조회해 결과로 수렴시키는 복구 use-case.
 * transactionKey 를 받았으면 key 로 단건 조회하고(by key), 못 받았으면 orderId 로 되물어(by order) 수렴시킨다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRecovery {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentConfirmation paymentConfirmation;
    private final PaymentRecoveryProperties properties;

    /**
     * transactionKey 를 받은 채 멈춘 결제를 key 로 단건 조회해 수렴시킨다. (콜백 유실 대비)
     */
    public void recoverStuckByKey() {
        paymentRepository.findStuckPending(stuckThreshold()).forEach(this::reconcileByKey);
    }

    /**
     * key 를 못 받은 채 멈춘 결제를 orderId 로 PG 에 되물어 수렴시킨다. (동기 타임아웃·서킷 강등 대비)
     */
    public void recoverStuckByOrder() {
        paymentRepository.findStuckPendingWithoutKey(stuckThreshold()).forEach(this::reconcileByOrder);
    }

    private void reconcileByKey(PaymentModel payment) {
        PaymentGatewayTransaction transaction =
            paymentGateway.getTransaction(payment.getUserId(), payment.getTransactionKey());
        paymentConfirmation.confirm(payment.getTransactionKey(), transaction.status(), transaction.reason());
    }

    private void reconcileByOrder(PaymentModel payment) {
        List<PaymentGatewayTransaction> transactions =
            paymentGateway.getTransactionsByOrder(payment.getUserId(), payment.getOrderId());
        paymentConfirmation.confirmByOrder(payment.getId(), PaymentGatewayTransaction.resolveFrom(transactions));
    }

    private ZonedDateTime stuckThreshold() {
        return ZonedDateTime.now().minus(properties.pendingThreshold());
    }
}
