package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentErrorCode;
import com.loopers.payment.domain.PaymentGatewayCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgProvider;
import com.loopers.support.error.CoreException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgPaymentGatewayTest {

    private static final Long USER_ID = 42L;
    private static final String ORDER_NUMBER = "20260624-000001";
    private static final long AMOUNT = 55_000L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String CALLBACK_BASE = "http://localhost:8080";

    private final TossPgClient tossPgClient = mock(TossPgClient.class);
    private final PgResilienceExecutor resilienceExecutor = mock(PgResilienceExecutor.class);
    private final PgPaymentGateway gateway = new PgPaymentGateway(tossPgClient, CALLBACK_BASE, resilienceExecutor);

    @SuppressWarnings("unchecked")
    private void resiliencePassThrough() {
        when(resilienceExecutor.call(any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(1)).get());
    }

    private PgApiResponse<PgTransactionResponse> pgResponse(String transactionKey, String status, String reason) {
        return new PgApiResponse<>(
                new PgApiResponse.Meta("SUCCESS", null, null),
                new PgTransactionResponse(transactionKey, status, reason));
    }

    private PaymentGatewayCommand command() {
        return new PaymentGatewayCommand(USER_ID, ORDER_NUMBER, AMOUNT, CardType.SAMSUNG, CARD_NO);
    }

    @Test
    @DisplayName("request 는 PG 응답을 TOSS provider 와 함께 PaymentGatewayResult 로 매핑한다")
    void givenPgReturnsPending_whenRequest_thenMapsResultWithTossProvider() {
        resiliencePassThrough();
        when(tossPgClient.request(any(), any())).thenReturn(pgResponse("20250816:TR:9577c5", "PENDING", null));

        PaymentGatewayResult result = gateway.request(command());

        assertAll(
                () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:9577c5"),
                () -> assertThat(result.pgProvider()).isEqualTo(PgProvider.TOSS),
                () -> assertThat(result.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(result.reason()).isNull()
        );
    }

    @Test
    @DisplayName("request 는 userId 헤더와 orderNumber·amount·cardType·cardNo·callbackUrl 을 PG 에 보낸다")
    void givenCommand_whenRequest_thenSendsPgPaymentRequest() {
        resiliencePassThrough();
        when(tossPgClient.request(any(), any())).thenReturn(pgResponse("tx-1", "PENDING", null));

        gateway.request(command());

        ArgumentCaptor<PgPaymentRequest> captor = ArgumentCaptor.forClass(PgPaymentRequest.class);
        verify(tossPgClient).request(eq("42"), captor.capture());
        PgPaymentRequest sent = captor.getValue();
        assertAll(
                () -> assertThat(sent.orderId()).isEqualTo(ORDER_NUMBER),
                () -> assertThat(sent.amount()).isEqualTo(AMOUNT),
                () -> assertThat(sent.cardType()).isEqualTo("SAMSUNG"),
                () -> assertThat(sent.cardNo()).isEqualTo(CARD_NO),
                () -> assertThat(sent.callbackUrl()).isEqualTo("http://localhost:8080/api/v1/payments/callback/toss")
        );
    }

    @Test
    @DisplayName("CB OPEN/RateLimiter 거절(안 닿음)이면 PAYMENT_GATEWAY_UNAVAILABLE 로 번역한다")
    void givenCallNotPermitted_whenRequest_thenThrowsGatewayUnavailable() {
        when(resilienceExecutor.call(any(), any())).thenThrow(mock(CallNotPermittedException.class));

        assertThatThrownBy(() -> gateway.request(command()))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }
}
