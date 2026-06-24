package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentInitiator paymentInitiator;
    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;

    public PaymentInfo pay(Long userId, PaymentCommand.Pay command) {
        PaymentInitiator.Initiated initiated = paymentInitiator.initiate(userId, command.orderId());

        PaymentGatewayResult ack = paymentGateway.requestPayment(new PaymentGatewayCommand(
            userId, command.orderId(), command.cardType(), command.cardNo(), initiated.amount()));

        PaymentModel payment = paymentService.assignTransactionKey(initiated.paymentId(), ack.transactionKey());
        return PaymentInfo.from(payment);
    }
}
