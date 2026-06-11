package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentModel savePayment(Long orderId, PaymentMethod method, BigDecimal amount, String transactionId, LocalDateTime approvedAt) {
        PaymentModel payment = new PaymentModel(orderId, method, amount, transactionId, approvedAt);
        return paymentRepository.save(payment);
    }
}
