package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Tx1 — Payment(PENDING) 저장 → 닻 확보(설계 §4.1).
     * 단일 save() 이고 PaymentFacade 가 비트랜잭션이므로, 이 save() 자체의 커밋이 PG 호출 이전의 커밋 경계가 된다.
     * (CLAUDE.md: 단일 save() 에는 @Transactional 을 붙이지 않는다.)
     */
    public PaymentModel createPending(Long orderId, String orderNumber, Long userId,
                                      CardType cardType, String cardNo, Long amount) {
        return paymentRepository.save(
                PaymentModel.pending(orderId, orderNumber, userId, cardType, cardNo, amount));
    }

    /**
     * Tx2 — PG 접수 응답으로 받은 transactionKey 를 반영한다(PENDING 유지). 관리 엔티티 dirty checking 으로 flush.
     */
    @Transactional
    public void attachTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = getById(paymentId);
        payment.attachTransactionKey(transactionKey);
    }

    /** grace 상한 초과 격리 — 조건부 UPDATE 로 PENDING 인 경우에만 UNKNOWN 으로 전이한다(동시 전이 race 안전). */
    @Transactional
    public void markUnknown(Long paymentId) {
        paymentRepository.transitionToUnknown(paymentId);
    }

    /** 수동 복구: UNKNOWN 격리 건을 PENDING 으로 되돌린다. */
    @Transactional
    public void restorePending(Long paymentId) {
        getById(paymentId).restorePending();
    }

    /**
     * 폴링: 미도달(주문 없음) 확정 — FAILED 로 떨군다(자동 재요청 X, 설계 §6.3).
     * 조건부 UPDATE 로 PENDING 인 경우에만 전이하고, affected=1 일 때만 후처리 신호를 반환한다.
     */
    @Transactional
    public ConfirmOutcome failUnreached(Long paymentId) {
        PaymentModel payment = getById(paymentId);
        int affected = paymentRepository.transitionToFailed(paymentId, "미도달: 결제가 PG 에 도달하지 않았습니다.");
        return affected == 1 ? ConfirmOutcome.failed(payment.getOrderId()) : ConfirmOutcome.skipped(payment.getOrderId());
    }

    /** 폴링 대상 조회: grace period(threshold) 이전 PENDING. */
    public List<PaymentModel> findPendingForReconcile(ZonedDateTime threshold) {
        return paymentRepository.findPendingOlderThan(threshold);
    }

    /** 요청 측 멱등성: 해당 주문의 활성(PENDING/PAID) 결제 조회. */
    public Optional<PaymentModel> findActive(Long orderId) {
        return paymentRepository.findActiveByOrderId(orderId);
    }

    public PaymentModel getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[paymentId = " + paymentId + "] 결제를 찾을 수 없습니다."));
    }

    /**
     * Tx3 — 콜백/폴링이 가져온 PG 결과로 전이를 확정한다.
     * <ol>
     *   <li>무결성 가드(설계 §6.2): amount·cardNo 가 우리가 저장한 값과 다르면 전이 거부 + UNKNOWN 격리.</li>
     *   <li>일치 시 조건부 UPDATE 로 전이 → affected=1 일 때만 후처리 신호(PAID/FAILED) 반환, 0 이면 SKIPPED(멱등).</li>
     * </ol>
     * 실제 주문 후처리(주문 확정/실패)는 이 메서드를 호출한 트랜잭션 경계(콜백/폴링 핸들러) 안에서 수행한다.
     */
    @Transactional
    public ConfirmOutcome confirm(String transactionKey, PaymentStatus resolvedStatus,
                                  String reason, Long pgAmount, String pgCardNo) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));

        if (!integrityMatches(payment, pgAmount, pgCardNo)) {
            // 금액 위변조 / 오배달 가능성 → 전이 거부. 조건부 UPDATE 로 PENDING 인 경우에만 격리한다
            // (terminal 불변 + 동시 전이 race 안전 — managed entity dirty update 가 아니라 WHERE status='PENDING').
            paymentRepository.transitionToUnknown(payment.getId());
            return ConfirmOutcome.isolated(payment.getOrderId());
        }

        if (resolvedStatus == PaymentStatus.PAID) {
            int affected = paymentRepository.transitionToPaid(payment.getId(), transactionKey);
            return affected == 1 ? ConfirmOutcome.paid(payment.getOrderId())
                    : ConfirmOutcome.skipped(payment.getOrderId());
        }
        if (resolvedStatus == PaymentStatus.FAILED) {
            int affected = paymentRepository.transitionToFailed(payment.getId(), reason);
            return affected == 1 ? ConfirmOutcome.failed(payment.getOrderId())
                    : ConfirmOutcome.skipped(payment.getOrderId());
        }
        // PG 결과가 아직 처리 중(PENDING) → 전이 보류
        return ConfirmOutcome.stillPending(payment.getOrderId());
    }

    /**
     * amount·cardNo 무결성 가드. 콜백 채널은 두 값을 항상 보내므로 완전 동작하고,
     * 폴링의 orderNumber 경로처럼 값이 없으면(null) 해당 항목은 검사를 건너뛴다(신뢰된 pull).
     * cardNo 는 동일 마스킹 규칙으로 정규화하여 비교한다.
     */
    private boolean integrityMatches(PaymentModel payment, Long pgAmount, String pgCardNo) {
        if (pgAmount != null && !pgAmount.equals(payment.getAmount())) {
            return false;
        }
        if (pgCardNo != null && !PaymentModel.mask(pgCardNo).equals(payment.getCardNo())) {
            return false;
        }
        return true;
    }
}
