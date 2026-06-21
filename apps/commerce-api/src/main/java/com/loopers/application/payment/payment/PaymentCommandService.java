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
}
