package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgTransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("domain")
class PaymentFacadeTest {

    private final PaymentApplicationService paymentApplicationService = mock(PaymentApplicationService.class);
    private final PaymentReconciler paymentReconciler = mock(PaymentReconciler.class);
    private final PgClient pgClient = mock(PgClient.class);
    private final PaymentFacade facade =
        new PaymentFacade(paymentApplicationService, paymentReconciler, pgClient, "http://localhost:8080/api/v1/payments/callback");

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 7L;

    private static PaymentCommand command() {
        return new PaymentCommand(ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451");
    }

    private static PaymentModel payment(String transactionKey, PaymentStatus status) {
        return new PaymentModel(PAYMENT_ID, USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
            transactionKey, status, null, null, null);
    }

    @Test
    @DisplayName("정상 흐름: 접수 후 PG 호출하고 거래키를 반영한다")
    void happyPath() {
        // arrange
        when(paymentApplicationService.register(USER_ID, command())).thenReturn(payment(null, PaymentStatus.PENDING));
        when(pgClient.requestPayment(any())).thenReturn(new PgPaymentResult("TR:abc", PgTransactionStatus.PENDING, null));
        when(paymentApplicationService.getPayment(PAYMENT_ID)).thenReturn(payment("TR:abc", PaymentStatus.PENDING));

        // act
        PaymentInfo info = facade.pay(USER_ID, command());

        // assert
        verify(pgClient).requestPayment(any());
        verify(paymentApplicationService).attachTransactionKey(eq(PAYMENT_ID), eq("TR:abc"));
        assertThat(info.transactionKey()).isEqualTo("TR:abc");
        assertThat(info.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("멱등성: 이미 거래키가 있는 결제는 PG 를 다시 호출하지 않는다")
    void idempotent() {
        // arrange
        when(paymentApplicationService.register(USER_ID, command())).thenReturn(payment("TR:exist", PaymentStatus.PENDING));

        // act
        PaymentInfo info = facade.pay(USER_ID, command());

        // assert
        verify(pgClient, never()).requestPayment(any());
        verify(paymentApplicationService, never()).attachTransactionKey(any(), any());
        assertThat(info.transactionKey()).isEqualTo("TR:exist");
    }

    @Test
    @DisplayName("PG 호출 실패(타임아웃 등): 예외를 삼키고 PENDING 으로 두어 재조정 대상으로 남긴다")
    void pgFailureKeepsPending() {
        // arrange
        when(paymentApplicationService.register(USER_ID, command())).thenReturn(payment(null, PaymentStatus.PENDING));
        when(pgClient.requestPayment(any())).thenThrow(new PgClientException("타임아웃"));
        when(paymentApplicationService.getPayment(PAYMENT_ID)).thenReturn(payment(null, PaymentStatus.PENDING));

        // act
        PaymentInfo info = facade.pay(USER_ID, command());

        // assert
        verify(paymentApplicationService, never()).attachTransactionKey(any(), any());
        assertThat(info.status()).isEqualTo("PENDING");
        assertThat(info.transactionKey()).isNull();
    }
}
