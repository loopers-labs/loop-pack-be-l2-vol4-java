package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentWriter {

    private final PaymentRepository paymentRepository;

    public PaymentCreationResult create(Payment payment) {
        return paymentRepository.create(payment);
    }

    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    public Payment completeIfPending(Payment payment) {
        return paymentRepository.completeIfPending(payment);
    }
}
