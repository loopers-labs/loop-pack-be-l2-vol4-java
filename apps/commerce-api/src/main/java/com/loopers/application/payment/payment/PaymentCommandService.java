package com.loopers.application.payment.payment;

import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createRequestedPayment(Long orderId, Long amount) {
        return paymentRepository.save(new Payment(orderId, amount));
    }

    @Transactional
    public Payment markProcessing(Long orderId, String transactionKey) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new com.loopers.support.error.CoreException(
                com.loopers.support.error.ErrorType.NOT_FOUND,
                "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."
            ));
        if (!payment.isRequested()) {
            return payment;
        }

        payment.markProcessing(transactionKey);
        return paymentRepository.save(payment);
    }
}
