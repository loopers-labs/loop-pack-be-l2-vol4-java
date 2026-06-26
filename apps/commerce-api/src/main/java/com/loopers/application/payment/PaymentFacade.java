package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.ConfirmOutcome;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 결제 유스케이스 조합기. <b>메서드 전체를 @Transactional 로 감싸지 않는다</b>(설계 §4) —
 * PG HTTP 호출이 트랜잭션 밖이어야 커넥션 점유로 인한 장애 전파를 막는다.
 * 트랜잭션 경계는 PaymentService 의 메서드들(Tx1/Tx2)에 있고, 그 사이에 PG 호출을 끼운다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PaymentInfo pay(String loginId, String loginPw, String orderNumber, CardType cardType, String cardNo) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        OrderModel order = orderService.getByOrderNumberAndValidateOwner(orderNumber, user.getId());

        // 요청 측 멱등성("따닥 클릭"): 이미 활성(PENDING/PAID) 결제가 있으면 PG 재호출 없이 멱등 반환
        Optional<PaymentModel> active = paymentService.findActive(order.getId());
        if (active.isPresent()) {
            return PaymentInfo.from(active.get());
        }

        Long amount = order.getFinalPrice().longValueExact();
        // Tx1 — 닻 확보(PENDING 커밋)
        PaymentModel payment = paymentService.createPending(
                order.getId(), order.getOrderNumber(), user.getId(), cardType, cardNo, amount);

        try {
            // 트랜잭션 밖 — CircuitBreaker/Timeout/Retry 가 감싸는 지점
            PgTransaction tx = paymentGateway.request(
                    new PgPaymentCommand(order.getOrderNumber(), cardType, cardNo, amount));
            // Tx2 — 접수 응답 반영(PENDING 유지)
            paymentService.attachTransactionKey(payment.getId(), tx.transactionKey());
        } catch (PaymentGatewayException | CallNotPermittedException e) {
            // 미도달(5xx)·Read Timeout·CB OPEN 모두 여기로 수렴 → PENDING 유지(폴링이 복구), 사용자에겐 "처리 중"
            log.warn("PG 요청 미확정, PENDING 유지: orderNumber={}", orderNumber, e);
        }

        return PaymentInfo.from(payment);
    }

    /**
     * 콜백 결과를 확정한다(PG status 문자열 → 우리 상태 매핑 후 위임).
     */
    public ConfirmOutcome confirmResult(String transactionKey, String pgStatus, String reason, Long amount, String cardNo) {
        return confirmResolved(transactionKey, mapPgStatus(pgStatus), reason, amount, cardNo);
    }

    /**
     * <b>confirm(조건부 UPDATE) + 주문 후처리를 한 트랜잭션</b>으로 묶어 "결제 PAID 인데 주문 미확정" crash gap 을 막는다
     * (외부 호출 없음 → 트랜잭션 안전). affected=1 일 때만 후처리한다. 콜백·폴링 양쪽이 공유하는 확정 경로다.
     */
    @Transactional
    public ConfirmOutcome confirmResolved(String transactionKey, PaymentStatus resolvedStatus,
                                          String reason, Long amount, String cardNo) {
        ConfirmOutcome outcome = paymentService.confirm(transactionKey, resolvedStatus, reason, amount, cardNo);
        applyOrderPostProcessing(outcome);
        return outcome;
    }

    private void applyOrderPostProcessing(ConfirmOutcome outcome) {
        switch (outcome.result()) {
            case PAID -> orderService.markPaid(outcome.orderId());
            case FAILED -> orderService.markPaymentFailed(outcome.orderId());
            default -> {
                // SKIPPED(멱등)/ISOLATED(격리)/STILL_PENDING(보류) → 주문 후처리 없음
            }
        }
    }

    private PaymentStatus mapPgStatus(String pgStatus) {
        if (pgStatus == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "콜백 상태가 비어 있습니다.");
        }
        return switch (pgStatus) {
            case "SUCCESS" -> PaymentStatus.PAID;
            case "FAILED" -> PaymentStatus.FAILED;
            case "PENDING" -> PaymentStatus.PENDING; // 처리 중 → 전이 보류
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "알 수 없는 콜백 상태: " + pgStatus);
        };
    }

    /**
     * 폴링/수동 복구의 단건 reconciliation. <b>비트랜잭션</b> — PG 조회(HTTP)를 트랜잭션 밖에서 수행하고
     * 결과 반영만 트랜잭션 경계(confirmResolved/failUnreached/markUnknown)에 위임한다.
     * <p>설계 §6.3 flowchart: <b>PG 를 먼저 조회</b>하고, 결과가 확정(SUCCESS/FAILED)이면 grace 상한과 무관하게 확정한다
     * (오래됐어도 돈이 빠진 SUCCESS 는 자동 PAID — "돈 빠졌는데 주문 누락" 방지). UNKNOWN 격리는
     * <b>PG 도 여전히 처리 중(PENDING)이면서 상한(10분)을 초과한</b> 경우에만 적용한다.
     */
    public ReconcileOutcome reconcile(PaymentModel payment) {
        Optional<PgTransaction> resolved = resolveFromGateway(payment);
        if (resolved.isEmpty()) {
            // 주문 없음(미도달) → FAILED 확정. 시스템 자동 재요청 X, 사용자 재시도에 맡긴다.
            failUnreached(payment.getId());
            return ReconcileOutcome.UNREACHED_FAILED;
        }

        PgTransaction tx = resolved.get();
        if (tx.status() == PaymentStatus.PENDING) {
            // 도달함, 결과 미정. 상한 초과면 격리, 아니면 다음 주기 재확인.
            if (payment.getCreatedAt().isBefore(ZonedDateTime.now().minusMinutes(10))) {
                paymentService.markUnknown(payment.getId());
                return ReconcileOutcome.ISOLATED;
            }
            return ReconcileOutcome.STILL_PROCESSING;
        }

        // PAID/FAILED 확정(상한과 무관) — transactionKey 가 없으면(orderNumber 로 되짚은 경우) 먼저 붙인다.
        String key = payment.getTransactionKey();
        if (key == null && tx.transactionKey() != null) {
            paymentService.attachTransactionKey(payment.getId(), tx.transactionKey());
            key = tx.transactionKey();
        }
        confirmResolved(key, tx.status(), tx.reason(), tx.amount(), null);
        return tx.status() == PaymentStatus.PAID ? ReconcileOutcome.PAID : ReconcileOutcome.FAILED;
    }

    /**
     * 수동 복구(운영자). UNKNOWN/오래된 PENDING 건을 강제로 재조회·확정한다(설계 §6.4).
     * UNKNOWN 은 폴링 대상이 아니므로(폴링은 PENDING 만 본다) 이 경로로만 되살릴 수 있다.
     */
    public ReconcileOutcome reconcileManually(Long paymentId) {
        PaymentModel payment = paymentService.getById(paymentId);
        if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            // 격리 해제: PG 결과로 다시 확정 시도. confirm 의 조건부 UPDATE 는 PENDING 만 전이시키므로
            // UNKNOWN → PENDING 으로 되돌린 뒤 표준 reconcile 경로를 태운다.
            paymentService.restorePending(paymentId);
            payment = paymentService.getById(paymentId);
        }
        return reconcile(payment);
    }

    private Optional<PgTransaction> resolveFromGateway(PaymentModel payment) {
        if (payment.getTransactionKey() != null) {
            return paymentGateway.findByTransactionKey(payment.getTransactionKey());
        }
        return paymentGateway.findByOrderId(payment.getOrderNumber()).stream().findFirst();
    }

    /** 미도달(주문 없음) → FAILED 확정 + 주문 실패 후처리(한 트랜잭션). */
    @Transactional
    public ConfirmOutcome failUnreached(Long paymentId) {
        ConfirmOutcome outcome = paymentService.failUnreached(paymentId);
        applyOrderPostProcessing(outcome);
        return outcome;
    }

    /** 결제 조회(클라이언트 폴링용). 확정된 DB 상태를 그대로 노출하며 소유자만 접근 가능하다. */
    public PaymentInfo getPayment(String loginId, String loginPw, Long paymentId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        PaymentModel payment = paymentService.getById(paymentId);
        if (!payment.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "해당 결제에 접근할 수 없습니다.");
        }
        return PaymentInfo.from(payment);
    }
}
