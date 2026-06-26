package com.loopers.domain.payment;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    private PaymentModel pending() {
        return new PaymentModel(1L, 10L, CardType.SAMSUNG, Money.of(5_000L));
    }

    @DisplayName("결제 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면 status=PENDING으로 초기화된다")
        @Test
        void createsPending_whenValid() {
            PaymentModel payment = pending();

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                () -> assertThat(payment.getAmount().value()).isEqualTo(5_000L),
                () -> assertThat(payment.getTransactionKey()).isNull()
            );
        }

        @DisplayName("orderId·userId·cardType이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRequiredIsNull() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new PaymentModel(null, 10L, CardType.KB, Money.of(100L))).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new PaymentModel(1L, null, CardType.KB, Money.of(100L))).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new PaymentModel(1L, 10L, null, Money.of(100L))).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }

        @DisplayName("결제 금액이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenAmountNotPositive() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 10L, CardType.SAMSUNG, Money.ZERO));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("거래키 연결 시")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PG가 발급한 거래키를 연결하면 transactionKey에 반영된다")
        @Test
        void assignsKey() {
            PaymentModel payment = pending();

            payment.assignTransactionKey("20250816:TR:9577c5");

            assertThat(payment.getTransactionKey()).isEqualTo("20250816:TR:9577c5");
        }
    }

    @DisplayName("결제 성공 확정(markSuccess) 시")
    @Nested
    class MarkSuccess {

        @DisplayName("PENDING이면 SUCCESS로 전이된다")
        @Test
        void pendingToSuccess() {
            PaymentModel payment = pending();

            payment.markSuccess();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("이미 SUCCESS인 결제를 다시 markSuccess해도 예외 없이 SUCCESS를 유지한다 (멱등)")
        @Test
        void isIdempotent() {
            PaymentModel payment = pending();
            payment.markSuccess();

            payment.markSuccess();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("이미 FAILED인 결제를 markSuccess하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenFailed() {
            PaymentModel payment = pending();
            payment.markFailed("한도 초과");

            CoreException ex = assertThrows(CoreException.class, payment::markSuccess);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결제 실패 확정(markFailed) 시")
    @Nested
    class MarkFailed {

        @DisplayName("PENDING이면 FAILED로 전이되고 사유가 기록된다")
        @Test
        void pendingToFailed() {
            PaymentModel payment = pending();

            payment.markFailed("한도 초과");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도 초과")
            );
        }

        @DisplayName("이미 FAILED인 결제를 다시 markFailed해도 예외 없이 FAILED를 유지한다 (멱등)")
        @Test
        void isIdempotent() {
            PaymentModel payment = pending();
            payment.markFailed("한도 초과");

            payment.markFailed("잘못된 카드");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("이미 SUCCESS인 결제를 markFailed하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenSuccess() {
            PaymentModel payment = pending();
            payment.markSuccess();

            CoreException ex = assertThrows(CoreException.class, () -> payment.markFailed("사유"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
