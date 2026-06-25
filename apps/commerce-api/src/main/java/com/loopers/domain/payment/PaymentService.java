package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * PG 호출 전에 결제를 PENDING 으로 먼저 적재한다. 외부 호출 결과와 무관하게
     * "결제를 시도했다"는 사실을 남겨, 타임아웃 등으로 키를 못 받아도 이후 복구가 그 행을 긁을 수 있게 한다.
     */
    @Transactional
    public PaymentModel createPending(Long userId, Long orderId, Long amount) {
        return paymentRepository.save(PaymentModel.createPending(userId, orderId, amount));
    }

    /**
     * PG 접수 응답으로 받은 transactionKey 를 결제에 반영한다. assignTransactionKey 가 멱등이라
     * 재시도/중복 수신에도 안전하다.
     */
    @Transactional
    public PaymentModel assignTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = getById(paymentId);
        payment.assignTransactionKey(transactionKey);
        return paymentRepository.save(payment);
    }

    /**
     * transactionKey 로 결제를 조회한다. (콜백·keyed 복구가 결과를 수렴시킬 결제를 확보하는 진입)
     */
    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND, "결제건을 찾을 수 없습니다. [transactionKey = " + transactionKey + "]"));
    }

    /**
     * paymentId 로 결제를 조회한다. (keyless 복구가 key 없이 결제를 확보하는 진입)
     */
    @Transactional(readOnly = true)
    public PaymentModel getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND, "결제건을 찾을 수 없습니다. [paymentId = " + paymentId + "]"));
    }

    /**
     * 결제 전이 결과를 영속한다. (공유 수렴점이 도메인 메서드로 전이시킨 뒤 저장)
     */
    @Transactional
    public PaymentModel save(PaymentModel payment) {
        return paymentRepository.save(payment);
    }
}
