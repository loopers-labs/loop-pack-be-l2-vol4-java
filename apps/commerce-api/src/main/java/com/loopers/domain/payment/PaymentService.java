package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentModel pay(String userNumber, Long orderId, String orderNumber, CardType cardType, String cardNo, BigDecimal amount) {
        PaymentModel payment = paymentRepository.save(new PaymentModel(orderId, cardType, cardNo, amount));

        PaymentGatewayResponse response = paymentGateway.requestPayment(
                new PaymentGatewayRequest(userNumber, orderNumber, cardType, cardNo, amount)
        );
        payment.updateTransactionKey(response.transactionKey());

        return paymentRepository.save(payment);
    }
}
