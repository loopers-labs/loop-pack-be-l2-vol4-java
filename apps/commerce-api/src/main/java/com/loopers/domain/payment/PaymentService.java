package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentModel pay(String userNumber, Long orderId, String orderNumber, CardType cardType, String cardNo, BigDecimal amount) {
        PaymentModel payment = paymentRepository.save(new PaymentModel(orderId, cardType, cardNo, amount));

        try {
            PaymentGatewayResponse response = paymentGateway.requestPayment(
                    new PaymentGatewayRequest(userNumber, orderNumber, cardType, cardNo, amount)
            );
            payment.update(response.transactionKey(), response.status());
        } catch (PaymentGatewayException e) {
            switch (e.getFailureReason()) {
                case RETRY_FAILED, UNKNOWN -> {
                    log.error("PG 결제 상태를 확정할 수 없어 UNKNOWN으로 처리합니다. orderId={}", orderId, e);
                    payment.markAsUnknown();
                }
                case BAD_REQUEST -> payment.markAsFailed();
            }
        }

        return paymentRepository.save(payment);
    }
}
