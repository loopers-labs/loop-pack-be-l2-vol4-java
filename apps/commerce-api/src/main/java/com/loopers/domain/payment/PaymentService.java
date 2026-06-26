package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 결제건을 생성한다.
     * @param userId 유저 ID
     * @param orderId 주문 ID
     * @param cardType 카드 종류
     * @param cardNo 카드 번호
     * @param amount 결제 금액
     * @return 결제건
     */
    @Transactional
    public PaymentModel create(Long userId, Long orderId, CardType cardType, String cardNo, Long amount) {
        return paymentRepository.save(new PaymentModel(userId, orderId, cardType, cardNo, amount));
    }

    /**
     * 결제건에 트랜잭션 키를 연결한다.
     * @param paymentId 결제건 ID
     * @param transactionKey 트랜잭션 키
     * @return 결제건
     */
    @Transactional
    public PaymentModel linkTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + paymentId + "] 결제건을 찾을 수 없습니다."));

        payment.linkTransactionKey(transactionKey);
        return paymentRepository.save(payment);
    }

    /**
     * PG 결과(콜백 또는 조회)를 결제건에 반영한다. 상태 전이는 PaymentModel이 멱등하게 보장하므로,
     * 중복 콜백이나 콜백↔조회 경합 상황에서도 안전하다.
     */
    @Transactional
    public PaymentModel applyResult(String transactionKey, PaymentStatus result, String reason) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제건을 찾을 수 없습니다."));

        switch (result) {
            case SUCCESS -> payment.markSuccess(reason);
            case FAILED -> payment.markFailed(reason);
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "반영할 결과는 SUCCESS 또는 FAILED여야 합니다.");
        }

        return paymentRepository.save(payment);
    }

    /**
     * 트랜잭션 키로 결제건을 조회한다.
     * @param transactionKey 트랜잭션 키
     * @return 결제건
     */
    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제건을 찾을 수 없습니다."));
    }
}
