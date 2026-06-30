package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentReader {

    private final PaymentRepository paymentRepository;

    public Optional<Payment> findPayment(Long orderId, String userLoginId) {
        return paymentRepository.findByOrderIdAndUserLoginId(orderId, userLoginId);
    }

    public Payment getPayment(Long orderId, String userLoginId) {
        return paymentRepository.findByOrderIdAndUserLoginId(orderId, userLoginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."));
    }

    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."));
    }

    public List<Payment> findPendingPaymentsForReconciliation(int limit) {
        return paymentRepository.findPendingPaymentsForReconciliation(limit);
    }
}
