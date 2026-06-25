package com.loopers.application.payment;

import com.loopers.application.order.OrderPaymentResultHandler;
import com.loopers.domain.payment.GatewayLookup;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;
    private final OrderPaymentResultHandler orderResultHandler;
    private final Duration pendingGrace;
    private final Duration stuckThreshold;

    public PaymentRecoveryService(
        PaymentRepository paymentRepository,
        PaymentGateway paymentGateway,
        PaymentService paymentService,
        OrderPaymentResultHandler orderResultHandler,
        @Value("${payment.recovery.pending-grace:PT30S}") Duration pendingGrace,
        @Value("${payment.recovery.stuck-threshold:PT10M}") Duration stuckThreshold
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
        this.orderResultHandler = orderResultHandler;
        this.pendingGrace = pendingGrace;
        this.stuckThreshold = stuckThreshold;
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
        ZonedDateTime cutoff = ZonedDateTime.now().minus(pendingGrace);
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

    /**
     * 결제는 SUCCESS인데 주문이 아직 반영되지 않은 건을 주문 반영으로 재구동한다.
     * (콜백·확정 직후 onPaid가 DB 장애·재배포 등으로 실패하면 결제 SUCCESS·주문 CREATED로 어긋나는데,
     *  PENDING만 보는 다른 복구가 못 줍는 사각지대다.) onPaid는 멱등이라 이미 PAID면 no-op.
     * 스캔 범위는 stuck-threshold 이내 최근 SUCCESS로 제한한다.
     * TODO(Round 7): in-process 이벤트의 한계 — Outbox + Kafka 재발행으로 이 backstop을 대체한다.
     */
    public void reapplySuccess() {
        ZonedDateTime since = ZonedDateTime.now().minus(stuckThreshold);
        for (PaymentModel payment : paymentRepository.findSuccessfulSince(since)) {
            try {
                orderResultHandler.onPaid(payment.getOrderId());
            } catch (Exception e) {
                log.warn("주문 반영 재구동 실패 — orderId={}", payment.getOrderId(), e);
            }
        }
    }

    /** 자동 복구로도 못 푼 채 너무 오래 PENDING인 건을 격리 대상으로 알린다(상태는 두고 사람이 점검). */
    public void flagStuck() {
        ZonedDateTime cutoff = ZonedDateTime.now().minus(stuckThreshold);
        List<PaymentModel> stuck = paymentRepository.findStuckPending(cutoff);
        if (!stuck.isEmpty()) {
            log.error("결제 STUCK 감지 — {}건이 {} 넘게 PENDING. 수동 점검 필요: orderIds={}",
                stuck.size(), stuckThreshold, stuck.stream().map(PaymentModel::getOrderId).toList());
        }
    }
}
