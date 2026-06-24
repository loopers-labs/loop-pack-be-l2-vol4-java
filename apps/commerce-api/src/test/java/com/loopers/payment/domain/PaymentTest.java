package com.loopers.payment.domain;

import com.loopers.common.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Money AMOUNT = Money.of(10_000L);
    private static final String TRANSACTION_KEY = "tx-0001";

    @Test
    @DisplayName("create 로 생성하면 PENDING 이고 키·provider 는 비어 있으며 terminal 이 아니다")
    void givenOrderIdAndAmount_whenCreate_thenPendingWithoutKey() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);

        assertAll(
                () -> assertThat(payment.getOrderId()).isEqualTo(ORDER_ID),
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
        Payment payment = Payment.create(ORDER_ID, AMOUNT);

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
        Payment payment = Payment.create(ORDER_ID, AMOUNT);

        payment.markSuccess();

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.isTerminal()).isTrue()
        );
    }

    @Test
    @DisplayName("PENDING 에서 markFailed 하면 FAILED 로 전이하고 사유를 기록하며 terminal 이 된다")
    void givenPending_whenMarkFailed_thenFailedWithReasonAndTerminal() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);

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
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
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
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        payment.markFailed("한도초과");

        payment.markSuccess();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
