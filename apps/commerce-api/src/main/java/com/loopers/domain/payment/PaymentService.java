package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 결제 도메인 서비스 — 상태 전이를 각각 작은 트랜잭션으로 끊어 처리한다.
 * <p>
 * 외부 PG 호출은 본 서비스가 알지 못한다 (Facade 가 담당). 도메인 서비스는 DB 상태만 안전하게 전이시킨다.
 */
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * PENDING 결제 레코드를 생성한다. orderId 중복 시 멱등 응답 — 기존 결제건 반환.
     * Facade 의 PG 호출 전에 별도 트랜잭션으로 커밋되어야 한다 (REQUIRES_NEW).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentModel createPending(Long orderId, Long userId, Long amount, CardType cardType, String cardNo) {
        try {
            PaymentModel payment = new PaymentModel(orderId, userId, amount, cardType, cardNo);
            return paymentRepository.save(payment);
        } catch (DataIntegrityViolationException race) {
            // UNIQUE(order_id) 위반 — 동시 중복 요청. 기존 결제건 반환.
            return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.CONFLICT,
                    "결제 생성 중 중복이 감지되었지만 기존 결제건도 조회되지 않습니다."));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRequested(Long paymentId, String transactionKey) {
        PaymentModel payment = getById(paymentId);
        payment.markRequested(transactionKey);
        paymentRepository.save(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTimeoutPending(Long paymentId, String reason) {
        PaymentModel payment = getById(paymentId);
        payment.markTimeoutPending(reason);
        paymentRepository.save(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Long paymentId) {
        PaymentModel payment = getById(paymentId);
        payment.markSucceeded();
        paymentRepository.save(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long paymentId, String reason) {
        PaymentModel payment = getById(paymentId);
        payment.markFailed(reason);
        paymentRepository.save(payment);
    }

    /**
     * 복구 폴링에서 PG GET (by orderId) 응답으로 transactionKey 를 채워주는 경로.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = getById(paymentId);
        payment.attachTransactionKey(transactionKey);
        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentModel getById(Long paymentId) {
        return paymentRepository.find(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + paymentId + "] 결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[orderId = " + orderId + "] 주문에 대한 결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> findRecoverable(ZonedDateTime requestedBefore, int limit) {
        return paymentRepository.findRecoverable(requestedBefore, limit);
    }
}
