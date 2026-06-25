package com.loopers.domain.payment.service;

import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.repository.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
