package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int MAX_RECOVERY_ATTEMPTS = 5;

    private final PaymentRepository paymentRepository;

    /**
     * PG 호출 전 PENDING 선삽입. order_id UNIQUE 가 멱등성 락 역할.
     * 동시 요청은 INSERT 시점의 UNIQUE 위반으로 하나만 통과한다(check-then-act 금지).
     */
    @Transactional
    public PaymentModel createPending(Long orderId, Long userId, Long amount, CardType cardType, String cardNo) {
        try {
            PaymentModel paymentModel = PaymentModel.of(orderId, userId, amount, cardType, cardNo);
            return paymentRepository.save(paymentModel);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "[orderId = " + orderId + "] 이미 결제가 접수된 주문입니다.");
        }
    }

    @Transactional
    public void markRequested(Long orderId, String transactionKey) {
        getByOrderId(orderId).markRequested(transactionKey);
    }

    @Transactional
    public void markAttemptedWithoutKey(Long orderId) {
        getByOrderId(orderId).markAttemptedWithoutKey();
    }

    /**
     * 콜백/폴링 공용 결과 반영. orderId 로 조회(우리 안정 키)하고, 타임아웃으로 key 를 못 받았던
     * 결제는 여기서 transactionKey 를 채워(reconcile) 둔다. 종결 상태면 무시(멱등) + 금액 대조.
     */
    @Transactional
    public PaymentModel applyResult(Long orderId, String transactionKey, PaymentStatus status, Long amount, String reason) {
        PaymentModel payment = getByOrderId(orderId);

        if (payment.getTransactionKey() == null && transactionKey != null) {
            payment.markRequested(transactionKey);
        }

        if (amount != null && !amount.equals(payment.getAmount())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        if (status == PaymentStatus.SUCCESS) {
            payment.succeed();
        } else if (status == PaymentStatus.FAILED) {
            payment.fail(reason);
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public PaymentModel getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> findRecoverable() {
        return paymentRepository.findRecoverable(MAX_RECOVERY_ATTEMPTS);
    }

    /**
     * 복구 폴링이 미해결로 끝난 1회를 기록. 상한 도달 시 더는 폴링 대상이 아니며(쿼리에서 제외),
     * 영구 미결로 남으므로 경보한다(수동 확인 대상).
     */
    @Transactional
    public void recordRecoveryAttempt(Long orderId) {
        PaymentModel payment = getByOrderId(orderId);
        payment.increaseRecoveryAttempts();
        if (payment.getRecoveryAttempts() >= MAX_RECOVERY_ATTEMPTS) {
            log.error("[orderId = {}] 결제 복구 폴링 {}회 초과 — PENDING 미해결, 수동 확인 필요.",
                    orderId, MAX_RECOVERY_ATTEMPTS);
        }
    }
}
