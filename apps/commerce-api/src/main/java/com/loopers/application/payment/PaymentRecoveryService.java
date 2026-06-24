package com.loopers.application.payment;

import com.loopers.domain.payment.GatewayLookup;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * 콜백 미수신·타임아웃으로 PENDING에 남은 결제를 PG에 직접 조회해 확정한다(복구).
 * PG가 응답하지 않으면(게이트웨이 fallback) 이번 주기는 건너뛰고 다음 주기에 재시도한다.
 */
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

    /** 거래키가 있는 PENDING을 PG에 조회해 확정한다. */
    public void reconcilePending() {
        for (PaymentModel payment : paymentRepository.findAllByStatus(PaymentStatus.PENDING)) {
            if (payment.getTransactionKey() == null) {
                continue;
            }
            paymentGateway.queryStatus(payment.getTransactionKey(), payment.getUserId())
                .ifPresent(status -> reflect(payment.getTransactionKey(), status));
        }
    }

    /**
     * 거래키조차 못 받은(요청 타임아웃) PENDING을 주문 기준으로 메꾼다 — 유예시간 경과 건만 대상.
     * PG에 거래 있으면 거래키 backfill 후 확정, 거래가 확실히 없으면 취소(보상), PG 장애면 다음 주기로 미룬다.
     */
    public void recoverKeyless() {
        ZonedDateTime cutoff = ZonedDateTime.now().minus(pendingTimeout);
        for (PaymentModel payment : paymentRepository.findKeylessPendingBefore(cutoff)) {
            GatewayLookup lookup = paymentGateway.queryByOrderId(payment.getOrderId(), payment.getUserId());
            switch (lookup.result()) {
                case FOUND -> {
                    paymentService.assignTransactionKey(payment.getOrderId(), lookup.transactionKey());
                    reflect(lookup.transactionKey(), lookup.status());
                }
                case NOT_FOUND -> paymentService.failByOrderId(payment.getOrderId(), "PG 미접수 (유예시간 경과)");
                case UNREACHABLE -> {
                    // PG 장애로 거래 유무 불명 — 취소하지 않고 다음 주기에 재시도
                }
            }
        }
    }

    private void reflect(String transactionKey, String pgStatus) {
        if ("SUCCESS".equals(pgStatus)) {
            paymentService.confirm(transactionKey, true, null);
        } else if ("FAILED".equals(pgStatus)) {
            paymentService.confirm(transactionKey, false, "PG 조회 결과 실패");
        }
    }
}
