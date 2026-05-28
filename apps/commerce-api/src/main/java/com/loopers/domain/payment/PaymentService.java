package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentModel save(PaymentModel payment) {
        return paymentRepository.save(payment);
    }

    /** 멱등 저장 — orderId 기준 이미 존재하면 기존 레코드 반환, 없으면 신규 저장 */
    public PaymentModel saveIfAbsent(UUID orderId, PaymentModel newPayment) {
        return paymentRepository.findByOrderId(orderId)
            .orElseGet(() -> paymentRepository.save(newPayment));
    }

    public PaymentModel getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."));
    }
}
