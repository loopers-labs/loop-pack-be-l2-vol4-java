package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentFacadeTest {

    private static final String LOGIN_ID = "tester01";
    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 1001L;
    private static final Long PAYMENT_ID = 50L;
    private static final String CARD_NO = "1234-5678-9814-1451";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PgPaymentClient pgClient = mock(PgPaymentClient.class);
    private final PaymentFacade paymentFacade = new PaymentFacade(userRepository, paymentService, pgClient);

    private void givenUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
    }

    private Payment pendingPayment() {
        Payment p = mock(Payment.class);
        when(p.getId()).thenReturn(PAYMENT_ID);
        when(p.getOrderId()).thenReturn(ORDER_ID);
        when(p.getAmount()).thenReturn(5000L);
        when(p.getStatus()).thenReturn(PaymentStatus.PENDING);
        return p;
    }

    private PgDto.Envelope<PgDto.TransactionResponse> pgSuccess(String key) {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("SUCCESS", null, null),
            new PgDto.TransactionResponse(key, "PENDING", null));
    }

    private PgDto.Envelope<PgDto.TransactionResponse> pgUnavailable() {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("FAIL", "PG_UNAVAILABLE", "미확정"), null);
    }

    @DisplayName("결제를 요청할 때, ")
    @Nested
    class RequestPayment {

        @DisplayName("PG가 거래키를 반환하면, 키를 결제에 반영하고 PENDING ACK를 돌려준다.")
        @Test
        void attachesKey_onPgSuccess() {
            // arrange
            givenUser();
            Payment pending = pendingPayment();
            when(paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG)).thenReturn(pending);
            when(pgClient.requestPayment(eq(String.valueOf(USER_ID)), any())).thenReturn(pgSuccess("TKEY"));
            Payment attached = mock(Payment.class);
            when(attached.getStatus()).thenReturn(PaymentStatus.PENDING);
            when(attached.getTransactionKey()).thenReturn("TKEY");
            when(attached.getOrderId()).thenReturn(ORDER_ID);
            when(attached.getAmount()).thenReturn(5000L);
            when(paymentService.attachTransactionKey(PAYMENT_ID, "TKEY")).thenReturn(attached);

            // act
            PaymentInfo info = paymentFacade.requestPayment(LOGIN_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO);

            // assert
            verify(paymentService).attachTransactionKey(PAYMENT_ID, "TKEY");
            assertAll(
                () -> assertThat(info.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(info.transactionKey()).isEqualTo("TKEY")
            );
        }

        @DisplayName("PG 호출이 미확정(fallback)이면, 키를 반영하지 않고 결제는 PENDING으로 유지한다.")
        @Test
        void keepsPending_onFallback() {
            // arrange
            givenUser();
            Payment pending = pendingPayment();
            when(paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG)).thenReturn(pending);
            when(pgClient.requestPayment(anyString(), any())).thenReturn(pgUnavailable());

            // act
            PaymentInfo info = paymentFacade.requestPayment(LOGIN_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO);

            // assert
            verify(paymentService, never()).attachTransactionKey(anyLong(), anyString());
            assertThat(info.status()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("회원이 없으면, NOT_FOUND 예외가 발생하고 PG 호출도 하지 않는다.")
        @Test
        void throwsNotFound_whenUserMissing() {
            // arrange
            when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.empty());

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.requestPayment(LOGIN_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(pgClient, never()).requestPayment(anyString(), any());
        }
    }
}
