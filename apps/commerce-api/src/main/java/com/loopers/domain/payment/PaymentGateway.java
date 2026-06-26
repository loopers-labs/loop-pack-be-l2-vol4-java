package com.loopers.domain.payment;

public interface PaymentGateway {

    PaymentRequestResult requestPayment(PaymentModel payment);

    PaymentTransactionStatus queryTransaction(PaymentModel payment);
}
