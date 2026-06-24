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
        PaymentModel payment = loadPayment(paymentId);
        payment.assignTransactionKey(transactionKey);
        return paymentRepository.save(payment);
    }

    /**
     * PG 결과(SUCCESS)를 결제에 반영한다. 콜백·폴링이 transactionKey 로 디스패치한다.
     * markSuccess 가 멱등이라 중복/순서뒤바뀜 수신에도 안전하다.
     */
    @Transactional
    public PaymentModel markSuccess(String transactionKey, String reason) {
        PaymentModel payment = loadByTransactionKey(transactionKey);
        payment.markSuccess(reason);
        return paymentRepository.save(payment);
    }

    /**
     * PG 결과(FAILED)를 결제에 반영한다. markFailed 가 멱등이라 중복/순서뒤바뀜 수신에도 안전하다.
     */
    @Transactional
    public PaymentModel markFailed(String transactionKey, String reason) {
        PaymentModel payment = loadByTransactionKey(transactionKey);
        payment.markFailed(reason);
        return paymentRepository.save(payment);
    }

    private PaymentModel loadPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND, "결제건을 찾을 수 없습니다. [paymentId = " + paymentId + "]"));
    }

    private PaymentModel loadByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND, "결제건을 찾을 수 없습니다. [transactionKey = " + transactionKey + "]"));
    }
}
