package com.loopers.infrastructure.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentGatewayImpl implements PaymentGateway {

    private static final String EXTERNAL_ORDER_ID_FORMAT = "%06d";

    private final PgSimulatorClient pgSimulatorClient;

    @Value("${pg-simulator.callback-url}")
    private String callbackUrl;

    @Override
    public String requestPayment(PaymentModel payment) {
        PgSimulatorDto.PaymentRequest request = new PgSimulatorDto.PaymentRequest(
            toExternalOrderId(payment.getOrderId()),
            payment.getCardType().name(),
            payment.getCardNo().value(),
            (long) payment.getAmount(),
            callbackUrl
        );

        return pgSimulatorClient.requestPayment(String.valueOf(payment.getUserId()), request)
            .data()
            .transactionKey();
    }

    private String toExternalOrderId(Long orderId) {
        return String.format(EXTERNAL_ORDER_ID_FORMAT, orderId);
    }
}
