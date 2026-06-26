package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    private static final Long ORDER_ID = 1L;
    private static final String ORDER_NUMBER = "20260626123456000001";
    private static final Long USER_ID = 10L;
    private static final String CARD_NO = "1234-5678-9814-1451";

    private PaymentModel pending() {
        return PaymentModel.pending(ORDER_ID, ORDER_NUMBER, USER_ID, CardType.SAMSUNG, CARD_NO, 5000L);
    }

    @DisplayName("결제를 접수(PENDING 생성)할 때,")
    @Nested
    class Pending {

        @DisplayName("유효한 입력이면 PENDING 상태로 생성되고 카드 번호는 마스킹되어 저장된다.")
        @Test
        void createsPendingAndMasksCardNo_whenValid() {
            // when
            PaymentModel payment = pending();

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getCardNo()).isEqualTo("1234-****-****-1451"),
                    () -> assertThat(payment.getAmount()).isEqualTo(5000L),
                    () -> assertThat(payment.getTransactionKey()).isNull(),
                    () -> assertThat(payment.getFailureReason()).isNull()
            );
        }

        @DisplayName("결제 금액이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsNotPositive() {
            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    PaymentModel.pending(ORDER_ID, ORDER_NUMBER, USER_ID, CardType.SAMSUNG, CARD_NO, 0L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("카드 번호 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCardNoFormatIsInvalid() {
            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    PaymentModel.pending(ORDER_ID, ORDER_NUMBER, USER_ID, CardType.SAMSUNG, "1234567898141451", 5000L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 상태를 전이할 때,")
    @Nested
    class Transition {

        @DisplayName("PENDING 에서 PAID 로 전이하면 transactionKey 가 반영된다.")
        @Test
        void transitionsToPaid_fromPending() {
            // given
            PaymentModel payment = pending();

            // when
            payment.markPaid("20260626:TR:abc123");

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo("20260626:TR:abc123")
            );
        }

        @DisplayName("PENDING 에서 FAILED 로 전이하면 실패 사유가 반영된다.")
        @Test
        void transitionsToFailed_fromPending() {
            // given
            PaymentModel payment = pending();

            // when
            payment.markFailed("한도초과");

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailureReason()).isEqualTo("한도초과")
            );
        }

        @DisplayName("PENDING 에서 UNKNOWN 으로 격리할 수 있다.")
        @Test
        void transitionsToUnknown_fromPending() {
            // given
            PaymentModel payment = pending();

            // when
            payment.markUnknown();

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        }

        @DisplayName("PAID 확정 후 FAILED 전이를 시도하면 CONFLICT 예외가 발생한다(terminal 불변).")
        @Test
        void throwsConflict_whenTransitionFromTerminal() {
            // given
            PaymentModel payment = pending();
            payment.markPaid("20260626:TR:abc123");

            // when
            CoreException result = assertThrows(CoreException.class, () -> payment.markFailed("한도초과"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("PAID 확정 후 다시 PAID 전이를 시도해도 멱등하게 무시된다.")
        @Test
        void ignoresIdempotently_whenSameTerminalTransition() {
            // given
            PaymentModel payment = pending();
            payment.markPaid("20260626:TR:abc123");

            // when
            payment.markPaid("20260626:TR:different");

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo("20260626:TR:abc123")
            );
        }

        @DisplayName("transactionKey 를 등록해도 PENDING 상태는 유지된다.")
        @Test
        void staysPending_whenAttachTransactionKey() {
            // given
            PaymentModel payment = pending();

            // when
            payment.attachTransactionKey("20260626:TR:abc123");

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo("20260626:TR:abc123")
            );
        }
    }
}
