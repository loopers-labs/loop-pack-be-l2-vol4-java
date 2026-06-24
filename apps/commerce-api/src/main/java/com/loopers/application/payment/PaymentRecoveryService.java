package com.loopers.application.payment;

import com.loopers.domain.payment.GatewayLookup;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;

@Component
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;
    private final Duration pendingTimeout;

    public PaymentRecoveryService(
        PaymentRepository paymentRepository,
        PaymentGateway paymentGateway,
        PaymentService paymentService,
        @Value("${payment.recovery.pending-timeout:PT5M}") Duration pendingTimeout
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
        this.pendingTimeout = pendingTimeout;
    }

    public void reconcilePending() {
        for (PaymentModel payment : paymentRepository.findAllByStatus(PaymentStatus.PENDING)) {
            if (payment.getTransactionKey() == null) {
                continue;
            }
            try {
                paymentGateway.queryStatus(payment.getTransactionKey(), payment.getUserId())
                    .ifPresent(s -> paymentService.confirmFromGatewayStatus(payment.getTransactionKey(), s.status(), s.reason()));
            } catch (ObjectOptimisticLockingFailureException alreadyConfirmed) {
                // 콜백·다른 인스턴스가 동시에 확정 — no-op, 다음 건으로 진행
            }
        }
    }

    /** 거래키조차 못 받은(요청 타임아웃) PENDING을 주문 기준으로 메꾼다 — 유예시간 경과 건만 대상. */
    public void recoverKeyless() {
        ZonedDateTime cutoff = ZonedDateTime.now().minus(pendingTimeout);
        for (PaymentModel payment : paymentRepository.findKeylessPendingBefore(cutoff)) {
            GatewayLookup lookup = paymentGateway.queryByOrderId(payment.getOrderId(), payment.getUserId());
            try {
                switch (lookup.result()) {
                    case FOUND -> {
                        paymentService.assignTransactionKey(payment.getOrderId(), lookup.transactionKey());
                        paymentService.confirmFromGatewayStatus(lookup.transactionKey(), lookup.status(), lookup.reason());
                    }
                    case NOT_FOUND -> paymentService.failByOrderId(payment.getOrderId(), "PG 미접수 (유예시간 경과)");
                    case UNREACHABLE -> {
                        // PG 장애로 거래 유무 불명 — 취소하지 않고 다음 주기에 재시도
                    }
                }
            } catch (ObjectOptimisticLockingFailureException alreadyConfirmed) {
                // 콜백·다른 인스턴스가 동시에 확정 — no-op, 다음 건으로 진행
            }
        }
    }
}
