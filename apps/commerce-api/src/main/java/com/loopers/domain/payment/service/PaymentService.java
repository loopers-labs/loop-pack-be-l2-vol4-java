package com.loopers.domain.payment.service;

import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.repository.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment initiate(Long orderId, Long memberId, CardType cardType, String cardNo, Long amount) {
        if (paymentRepository.existsActiveByOrderId(orderId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 진행 중이거나 완료된 결제가 있습니다.");
        }
        return paymentRepository.save(Payment.create(orderId, memberId, cardType, cardNo, amount));
    }

    @Transactional
    public void assignTransactionKey(Long paymentId, String transactionKey) {
        Payment payment = getPayment(paymentId);
        payment.assignTransactionKey(transactionKey);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Payment> findRecoverablePayments() {
        return paymentRepository.findPendingWithTransactionKey();
    }

    /**
     * PG 결과(콜백/폴링)를 반영한다. 이미 확정된 결제는 무시한다(멱등).
     *
     * @return 이번 호출로 새로 확정되었으면 해당 Payment, 이미 확정되어 있었으면 null
     */
    @Transactional
    public Payment confirmResult(String transactionKey, PaymentStatus status, String reason) {
        Payment payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        if (!payment.isPending()) {
            return null;
        }
        switch (status) {
            case SUCCESS -> payment.markSuccess(reason);
            case FAILED -> payment.markFailed(reason);
            default -> {
                return null;
            }
        }
        return payment;
    }
}
