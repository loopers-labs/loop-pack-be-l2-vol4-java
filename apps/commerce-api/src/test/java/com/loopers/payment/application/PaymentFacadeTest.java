package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentFacadeTest {

    private static final String ORDER_NUMBER = "20260624-000001";
    private static final long AMOUNT = 55_000L;
    private static final Long PAYMENT_ID = 1L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TRANSACTION_KEY = "tx-0001";

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
    private final PaymentFacade paymentFacade = new PaymentFacade(paymentService, paymentGateway);

    private void stubHappyPath() {
        when(paymentService.createPending(ORDER_NUMBER))
                .thenReturn(new PaymentResult.Pending(PAYMENT_ID, ORDER_NUMBER, AMOUNT));
        when(paymentGateway.request(any()))
                .thenReturn(new PaymentGatewayResult(TRANSACTION_KEY, PgProvider.TOSS, PaymentStatus.PENDING, null));
    }

    private PaymentCommand.Pay payCommand() {
        return new PaymentCommand.Pay(ORDER_NUMBER, CardType.SAMSUNG, CARD_NO);
    }

    @Test
    @DisplayName("pay 는 PENDING 생성(TX1) → PG 요청 → 거래키 확정(TX2) 순서로 처리하고 접수 결과를 반환한다")
    void givenPayCommand_whenPay_thenCreatePendingRequestThenAssign() {
        stubHappyPath();

        PaymentResult.Accepted accepted = paymentFacade.pay(payCommand());

        InOrder inOrder = inOrder(paymentService, paymentGateway);
        inOrder.verify(paymentService).createPending(ORDER_NUMBER);
        inOrder.verify(paymentGateway).request(any());
        inOrder.verify(paymentService).assignTransaction(PAYMENT_ID, TRANSACTION_KEY, PgProvider.TOSS);
        assertAll(
                () -> assertThat(accepted.paymentId()).isEqualTo(PAYMENT_ID),
                () -> assertThat(accepted.orderNumber()).isEqualTo(ORDER_NUMBER),
                () -> assertThat(accepted.status()).isEqualTo(PaymentStatus.PENDING)
        );
    }

    @Test
    @DisplayName("pay 는 PG 에 orderNumber·서버계산 amount·cardType·cardNo 를 넘긴다")
    void givenPayCommand_whenPay_thenPassesGatewayCommand() {
        stubHappyPath();

        paymentFacade.pay(payCommand());

        ArgumentCaptor<PaymentGatewayCommand> captor = ArgumentCaptor.forClass(PaymentGatewayCommand.class);
        verify(paymentGateway).request(captor.capture());
        PaymentGatewayCommand sent = captor.getValue();
        assertAll(
                () -> assertThat(sent.orderNumber()).isEqualTo(ORDER_NUMBER),
                () -> assertThat(sent.amount()).isEqualTo(AMOUNT),
                () -> assertThat(sent.cardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(sent.cardNo()).isEqualTo(CARD_NO)
        );
    }
}
