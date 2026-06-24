package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayTransaction;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * 콜백이 유실돼 일정 시간 이상 PENDING 으로 멈춘 결제를, PG 에 직접 상태를 조회해 결과로 수렴시키는 복구 use-case.
 */
@Component
@RequiredArgsConstructor
public class PaymentRecovery {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentConfirmation paymentConfirmation;
    private final PaymentRecoveryProperties properties;

    public void recoverStuck() {
        ZonedDateTime threshold = ZonedDateTime.now().minus(properties.pendingThreshold());
        paymentRepository.findStuckPending(threshold).forEach(this::reconcile);
    }

    private void reconcile(PaymentModel payment) {
        PaymentGatewayTransaction transaction =
            paymentGateway.getTransaction(payment.getUserId(), payment.getTransactionKey());
        paymentConfirmation.confirm(payment.getTransactionKey(), transaction.status(), transaction.reason());
    }
}
