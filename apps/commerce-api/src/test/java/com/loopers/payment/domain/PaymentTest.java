package com.loopers.payment.domain;

import com.loopers.common.domain.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentTest {

    private static final Long USER_ID = 1L;
    private static final String ORDER_NUMBER = "20260624-000001";
    private static final Money AMOUNT = Money.of(10_000L);
    private static final String TRANSACTION_KEY = "tx-0001";

    @Test
    @DisplayName("create 로 생성하면 PENDING 이고 키·provider 는 비어 있으며 terminal 이 아니다")
    void givenOrderNumberAndAmount_whenCreate_thenPendingWithoutKey() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        assertAll(
                () -> assertThat(payment.getOrderNumber()).isEqualTo(ORDER_NUMBER),
                () -> assertThat(payment.getAmount()).isEqualTo(AMOUNT),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(payment.getPgProvider()).isNull(),
                () -> assertThat(payment.getReason()).isNull(),
                () -> assertThat(payment.isTerminal()).isFalse()
        );
    }

    @Test
    @DisplayName("assignTransaction 은 키·provider 만 채우고 상태는 PENDING 을 유지한다")
    void givenPending_whenAssignTransaction_thenFillsKeyAndProviderStaysPending() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        payment.assignTransaction(TRANSACTION_KEY, PgProvider.KAKAO);

        assertAll(
                () -> assertThat(payment.getTransactionKey()).isEqualTo(TRANSACTION_KEY),
                () -> assertThat(payment.getPgProvider()).isEqualTo(PgProvider.KAKAO),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING)
        );
    }

    @Test
    @DisplayName("PENDING 에서 markSuccess 하면 SUCCESS 로 전이하고 terminal 이 된다")
    void givenPending_whenMarkSuccess_thenSuccessAndTerminal() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        payment.markSuccess();

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.isTerminal()).isTrue()
        );
    }

    @Test
    @DisplayName("PENDING 에서 markFailed 하면 FAILED 로 전이하고 사유를 기록하며 terminal 이 된다")
    void givenPending_whenMarkFailed_thenFailedWithReasonAndTerminal() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        payment.markFailed("한도초과");

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도초과"),
                () -> assertThat(payment.isTerminal()).isTrue()
        );
    }

    @Test
    @DisplayName("이미 SUCCESS 인 결제에 markFailed 가 와도 무시하고 SUCCESS 를 유지한다")
    void givenSuccess_whenMarkFailed_thenIgnored() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);
        payment.markSuccess();

        payment.markFailed("한도초과");

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getReason()).isNull()
        );
    }

    @Test
    @DisplayName("이미 FAILED 인 결제에 markSuccess 가 와도 무시하고 FAILED 를 유지한다")
    void givenFailed_whenMarkSuccess_thenIgnored() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);
        payment.markFailed("한도초과");

        payment.markSuccess();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("markAbandoned 하면 ABANDONED 로 전이하고 사유를 기록하며 terminal 이 된다")
    void givenPending_whenMarkAbandoned_thenAbandonedWithReasonAndTerminal() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        payment.markAbandoned("정합성 보정 회수 실패");

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ABANDONED),
                () -> assertThat(payment.getReason()).isEqualTo("정합성 보정 회수 실패"),
                () -> assertThat(payment.isTerminal()).isTrue()
        );
    }

    @Test
    @DisplayName("이미 SUCCESS 인 결제에 markAbandoned 가 와도 무시하고 SUCCESS 를 유지한다")
    void givenSuccess_whenMarkAbandoned_thenIgnored() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);
        payment.markSuccess();

        payment.markAbandoned("회수 실패");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("verifyCallback 은 orderNumber·amount 가 일치하면 통과한다")
    void givenMatchingCallback_whenVerify_thenOk() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        assertThatCode(() -> payment.verifyCallback(ORDER_NUMBER, AMOUNT.value()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verifyCallback 은 amount 가 다르면 PAYMENT_CALLBACK_INVALID 가 발생한다")
    void givenAmountMismatch_whenVerify_thenThrows() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        assertThatThrownBy(() -> payment.verifyCallback(ORDER_NUMBER, AMOUNT.value() + 1))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_CALLBACK_INVALID);
    }

    @Test
    @DisplayName("verifyCallback 은 orderNumber 가 다르면 PAYMENT_CALLBACK_INVALID 가 발생한다")
    void givenOrderNumberMismatch_whenVerify_thenThrows() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, AMOUNT);

        assertThatThrownBy(() -> payment.verifyCallback("20991231-999999", AMOUNT.value()))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_CALLBACK_INVALID);
    }
}
