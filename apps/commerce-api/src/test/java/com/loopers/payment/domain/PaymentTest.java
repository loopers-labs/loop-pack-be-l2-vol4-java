package com.loopers.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final long AMOUNT = 5_000L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String MASKED_CARD_NO = "1234-****-****-1451";
    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";
    private static final String SUCCESS_REASON = "정상 승인되었습니다.";
    private static final String FAILED_REASON = "한도초과입니다. 다른 카드를 선택해주세요.";
    private static final ZonedDateTime REQUESTED_AT = ZonedDateTime.parse("2026-06-24T10:00:00+09:00");
    private static final ZonedDateTime COMPLETED_AT = ZonedDateTime.parse("2026-06-24T10:00:05+09:00");

    @DisplayName("PG 요청이 성공하면, 결제는 PENDING 상태와 PG 거래 정보를 가진다.")
    @Test
    void createsPendingPayment_whenPgRequestSucceeds() {
        // arrange & act
        Payment payment = createPendingPayment();

        // assert
        assertAll(
            () -> assertThat(payment.getUserId()).isEqualTo(USER_ID),
            () -> assertThat(payment.getOrderId()).isEqualTo(ORDER_ID),
            () -> assertThat(payment.getAmount()).isEqualTo(AMOUNT),
            () -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
            () -> assertThat(payment.getMaskedCardNo()).isEqualTo(MASKED_CARD_NO),
            () -> assertThat(payment.getPgTransactionKey()).isEqualTo(TRANSACTION_KEY),
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
            () -> assertThat(payment.getPgStatus()).isEqualTo(PgPaymentStatus.PENDING),
            () -> assertThat(payment.getFailureReason()).isNull(),
            () -> assertThat(payment.getRequestedAt()).isEqualTo(REQUESTED_AT),
            () -> assertThat(payment.getCompletedAt()).isNull()
        );
    }

    @DisplayName("PG 성공 결과를 반영하면, 결제는 SUCCEEDED 상태가 된다.")
    @Test
    void marksSucceeded_whenPgResultIsSuccess() {
        // arrange
        Payment payment = createPendingPayment();

        // act
        payment.markSucceeded(TRANSACTION_KEY, SUCCESS_REASON, COMPLETED_AT);

        // assert
        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED),
            () -> assertThat(payment.getPgStatus()).isEqualTo(PgPaymentStatus.SUCCESS),
            () -> assertThat(payment.getPgReason()).isEqualTo(SUCCESS_REASON),
            () -> assertThat(payment.getFailureReason()).isNull(),
            () -> assertThat(payment.getCompletedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    @DisplayName("PG 실패 결과를 반영하면, 결제는 FAILED 상태와 실패 사유를 기록한다.")
    @Test
    void marksFailed_whenPgResultIsFailed() {
        // arrange
        Payment payment = createPendingPayment();

        // act
        payment.markFailed(TRANSACTION_KEY, PaymentFailureReason.LIMIT_EXCEEDED, FAILED_REASON, COMPLETED_AT);

        // assert
        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
            () -> assertThat(payment.getPgStatus()).isEqualTo(PgPaymentStatus.FAILED),
            () -> assertThat(payment.getPgReason()).isEqualTo(FAILED_REASON),
            () -> assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.LIMIT_EXCEEDED),
            () -> assertThat(payment.getCompletedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    @DisplayName("PG 요청 타임아웃이 발생하면, 결제는 UNKNOWN 상태로 기록된다.")
    @Test
    void createsUnknownPayment_whenPgRequestTimesOut() {
        // arrange & act
        Payment payment = Payment.unknown(USER_ID, ORDER_ID, AMOUNT, CardType.SAMSUNG, CARD_NO, REQUESTED_AT);

        // assert
        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN),
            () -> assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.PG_TIMEOUT),
            () -> assertThat(payment.getPgTransactionKey()).isNull(),
            () -> assertThat(payment.getPgStatus()).isNull(),
            () -> assertThat(payment.getPgReason()).isNull(),
            () -> assertThat(payment.getCompletedAt()).isNull()
        );
    }

    @DisplayName("PG 요청이 즉시 실패하면, 결제는 REQUEST_FAILED 상태로 기록된다.")
    @Test
    void createsRequestFailedPayment_whenPgRequestFailsImmediately() {
        // arrange & act
        Payment payment = Payment.requestFailed(USER_ID, ORDER_ID, AMOUNT, CardType.SAMSUNG, CARD_NO, REQUESTED_AT);

        // assert
        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUEST_FAILED),
            () -> assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.PG_REQUEST_FAILED),
            () -> assertThat(payment.getPgTransactionKey()).isNull(),
            () -> assertThat(payment.getPgStatus()).isNull(),
            () -> assertThat(payment.getPgReason()).isNull()
        );
    }

    @DisplayName("이미 성공한 결제에 중복 성공 결과가 오면, 기존 성공 상태를 유지한다.")
    @Test
    void keepsSucceeded_whenDuplicateSuccessResultArrives() {
        // arrange
        Payment payment = createPendingPayment();
        payment.markSucceeded(TRANSACTION_KEY, SUCCESS_REASON, COMPLETED_AT);

        // act
        payment.markSucceeded(TRANSACTION_KEY, "중복 성공 콜백", COMPLETED_AT.plusSeconds(1));

        // assert
        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED),
            () -> assertThat(payment.getPgReason()).isEqualTo(SUCCESS_REASON),
            () -> assertThat(payment.getCompletedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    @DisplayName("이미 실패한 결제에 중복 실패 결과가 오면, 기존 실패 상태를 유지한다.")
    @Test
    void keepsFailed_whenDuplicateFailedResultArrives() {
        // arrange
        Payment payment = createPendingPayment();
        payment.markFailed(TRANSACTION_KEY, PaymentFailureReason.LIMIT_EXCEEDED, FAILED_REASON, COMPLETED_AT);

        // act
        payment.markFailed(TRANSACTION_KEY, PaymentFailureReason.INVALID_CARD, "중복 실패 콜백", COMPLETED_AT.plusSeconds(1));

        // assert
        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
            () -> assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.LIMIT_EXCEEDED),
            () -> assertThat(payment.getPgReason()).isEqualTo(FAILED_REASON),
            () -> assertThat(payment.getCompletedAt()).isEqualTo(COMPLETED_AT)
        );
    }

    private Payment createPendingPayment() {
        return Payment.pending(USER_ID, ORDER_ID, AMOUNT, CardType.SAMSUNG, CARD_NO, TRANSACTION_KEY, REQUESTED_AT);
    }
}
