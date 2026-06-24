package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentModel create(Long orderId, CardType cardType, String cardNo, BigDecimal amount) {
        return paymentRepository.save(new PaymentModel(orderId, cardType, cardNo, amount));
    }

    public PaymentModel applyPgResult(PaymentModel payment, String transactionKey, PaymentStatus status) {
        payment.applyPgResult(transactionKey, status);
        return paymentRepository.save(payment);
    }
}
