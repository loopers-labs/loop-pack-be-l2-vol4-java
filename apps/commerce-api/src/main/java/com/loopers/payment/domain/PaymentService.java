package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment savePayment(Payment payment) {
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다."));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByPgTransactionKey(String pgTransactionKey) {
        return paymentRepository.findByPgTransactionKey(pgTransactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 PG 거래입니다."));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findLatestPaymentByOrderId(Long orderId) {
        return paymentRepository.findLatestByOrderId(orderId);
    }
}
