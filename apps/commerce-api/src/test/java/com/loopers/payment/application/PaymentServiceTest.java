package com.loopers.payment.application;

import com.loopers.common.domain.Money;
import com.loopers.payment.domain.PaymentGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentServiceTest {

    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
    private final PaymentService paymentService = new PaymentService(paymentGateway);

    @Test
    @DisplayName("pay 는 주문 식별자와 금액으로 결제 게이트웨이를 호출한다")
    void givenOrderIdAndAmount_whenPay_thenRequestsPaymentToGateway() {
        paymentService.pay(1L, Money.of(58_000L));

        verify(paymentGateway).requestPayment(1L, Money.of(58_000L));
    }
}
