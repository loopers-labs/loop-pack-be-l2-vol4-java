package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    private PaymentModel createValidPayment() {
        return new PaymentModel(1L, 1L, CardType.SAMSUNG, "1234-5678-9014-1451", 10_000);
    }

    private PaymentModel createPaymentWithStatus(PaymentStatus status) {
        PaymentModel payment = createValidPayment();
        if (status == PaymentStatus.IN_PROGRESS) {
            payment.startProgress("TX-TEST");
        } else if (status != PaymentStatus.PENDING) {
            ReflectionTestUtils.setField(payment, "status", status);
        }
        return payment;
    }

    @Nested
    @DisplayName("PaymentModel 생성 시,")
    class Create {

        @Test
        @DisplayName("유효한 파라미터로 생성 시 PENDING 상태로 초기화된다.")
        void initializesWithPendingStatus_whenValidParams() {
            PaymentModel payment = createValidPayment();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPgTransactionId()).isNull();
            assertThat(payment.getFailureCode()).isNull();
        }

        @Test
        @DisplayName("orderId가 null이면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenOrderIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentModel(null, 1L, CardType.SAMSUNG, "1234", 10_000));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("cardNo가 blank이면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenCardNoIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, CardType.SAMSUNG, "  ", 10_000));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("amount가 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenAmountIsZeroOrNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, CardType.SAMSUNG, "1234", 0));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("startProgress() 호출 시,")
    class StartProgress {

        @Test
        @DisplayName("PENDING 상태에서 호출하면 IN_PROGRESS로 전환되고 pgTransactionId가 저장된다.")
        void transitionsToInProgress_whenStatusIsPending() {
            PaymentModel payment = createValidPayment();

            payment.startProgress("TX-123");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
            assertThat(payment.getPgTransactionId()).isEqualTo("TX-123");
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 호출하면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenStatusIsNotPending() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);

            CoreException ex = assertThrows(CoreException.class,
                () -> payment.startProgress("TX-456"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("pgTransactionId가 blank이면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenPgTransactionIdIsBlank() {
            PaymentModel payment = createValidPayment();

            CoreException ex = assertThrows(CoreException.class,
                () -> payment.startProgress("  "));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("markSuccess() 호출 시,")
    class MarkSuccess {

        @Test
        @DisplayName("IN_PROGRESS 상태에서 호출하면 SUCCESS로 전환된다.")
        void transitionsToSuccess_whenStatusIsInProgress() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);

            payment.markSuccess();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("IN_PROGRESS가 아닌 상태에서 호출하면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenStatusIsNotInProgress() {
            PaymentModel payment = createValidPayment(); // PENDING

            CoreException ex = assertThrows(CoreException.class, payment::markSuccess);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("markFailed() 호출 시,")
    class MarkFailed {

        @Test
        @DisplayName("PENDING 상태에서 호출하면 FAILED로 전환되고 failureCode가 저장된다.")
        void transitionsToFailed_fromPending() {
            PaymentModel payment = createValidPayment();

            payment.markFailed("LIMIT_EXCEEDED");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureCode()).isEqualTo("LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서도 FAILED로 전환된다.")
        void transitionsToFailed_fromInProgress() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);

            payment.markFailed("INVALID_CARD");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("SUCCESS 상태에서 호출하면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenStatusIsSuccess() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.SUCCESS);

            CoreException ex = assertThrows(CoreException.class,
                () -> payment.markFailed("ERROR"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("markAborted() 호출 시,")
    class MarkAborted {

        @Test
        @DisplayName("PENDING 상태에서 호출하면 ABORTED로 전환된다.")
        void transitionsToAborted_fromPending() {
            PaymentModel payment = createValidPayment();

            payment.markAborted();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ABORTED);
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서도 ABORTED로 전환된다.")
        void transitionsToAborted_fromInProgress() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);

            payment.markAborted();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ABORTED);
        }

        @Test
        @DisplayName("SUCCESS 상태에서 호출하면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenStatusIsSuccess() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.SUCCESS);

            CoreException ex = assertThrows(CoreException.class, payment::markAborted);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("applyPgResult() 호출 시,")
    class ApplyPgResult {

        @Test
        @DisplayName("ABORTED 상태에서 PG SUCCESS이면 SUCCESS로 전환되고 pgTransactionId가 소급 저장된다.")
        void transitionsToSuccess_whenPgReturnsSuccess() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.ABORTED);

            payment.applyPgResult("TX-RECOVERED", "SUCCESS", null);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPgTransactionId()).isEqualTo("TX-RECOVERED");
        }

        @Test
        @DisplayName("ABORTED 상태에서 PG FAILED이면 FAILED로 전환되고 failureCode가 저장된다.")
        void transitionsToFailed_whenPgReturnsFailed() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.ABORTED);

            payment.applyPgResult("TX-123", "FAILED", "LIMIT_EXCEEDED");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureCode()).isEqualTo("LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("이미 SUCCESS 상태이면 변경되지 않는다 (멱등성).")
        void doesNotChangeStatus_whenAlreadySuccess() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.SUCCESS);

            payment.applyPgResult("TX-OTHER", "FAILED", "ERROR");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("PG 상태가 PENDING이면 현재 상태를 유지한다.")
        void doesNotChangeStatus_whenPgIsPending() {
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.ABORTED);

            payment.applyPgResult("TX-123", "PENDING", null);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ABORTED);
        }
    }

    @Nested
    @DisplayName("hasPgTransactionRecord() 호출 시,")
    class HasPgTransactionRecord {

        @Test
        @DisplayName("pgTransactionId가 null이면 false를 반환한다.")
        void returnsFalse_whenPgTransactionIdIsNull() {
            PaymentModel payment = createValidPayment();

            assertThat(payment.hasPgTransactionRecord()).isFalse();
        }

        @Test
        @DisplayName("startProgress() 이후에는 true를 반환한다.")
        void returnsTrue_whenPgTransactionIdIsSet() {
            PaymentModel payment = createValidPayment();
            payment.startProgress("TX-123");

            assertThat(payment.hasPgTransactionRecord()).isTrue();
        }
    }
}
