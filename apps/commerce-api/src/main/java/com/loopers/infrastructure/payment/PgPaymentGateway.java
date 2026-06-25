package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private final PgClient pgClient;

    @Override
    public PaymentGatewayResult requestPayment(PaymentGatewayCommand command) {
        PgPaymentRequest request = new PgPaymentRequest(
            command.orderId(),
            command.cardType().name(),
            command.cardNo(),
            command.amount(),
            command.callbackUrl()
        );

        PgPaymentResponse data = pgClient.requestPayment(command.userId(), request).data();

        return new PaymentGatewayResult(
            data.transactionKey(),
            PaymentStatus.valueOf(data.status()),
            data.reason()
        );
    }
}
