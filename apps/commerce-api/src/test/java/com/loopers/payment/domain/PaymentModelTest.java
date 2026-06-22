package com.loopers.payment.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new PaymentModel(null, "TX-001234", "SAMSUNG", 10000L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("transactionKey가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTransactionKeyIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new PaymentModel(1L, null, "SAMSUNG", 10000L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("transactionKey가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTransactionKeyIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new PaymentModel(1L, " ", "SAMSUNG", 10000L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 값으로 생성하면, status는 PENDING이다.")
        @Test
        void createsPayment_withPendingStatus_whenAllFieldsAreValid() {
            // arrange & act
            PaymentModel payment = new PaymentModel(1L, "TX-001234", "SAMSUNG", 10000L);

            // assert
            assertAll(
                () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("TX-001234"),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING)
            );
        }
    }

    @DisplayName("결제를 확정(confirm)할 때,")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태이면, status가 SUCCESS로 변경된다.")
        @Test
        void changesStatusToSuccess_whenStatusIsPending() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, "TX-001234", "SAMSUNG", 10000L);

            // act
            payment.confirm();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("이미 SUCCESS인 상태에서 confirm()을 재호출하면, 무시된다.")
        @Test
        void doesNothing_whenStatusIsAlreadySuccess() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, "TX-001234", "SAMSUNG", 10000L);
            payment.confirm();

            // act & assert (예외 없이 실행)
            payment.confirm();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }

    @DisplayName("결제를 실패(fail)할 때,")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태이면, status가 FAILED로 변경된다.")
        @Test
        void changesStatusToFailed_whenStatusIsPending() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, "TX-001234", "SAMSUNG", 10000L);

            // act
            payment.fail();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("이미 FAILED인 상태에서 fail()을 재호출하면, 무시된다.")
        @Test
        void doesNothing_whenStatusIsAlreadyFailed() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, "TX-001234", "SAMSUNG", 10000L);
            payment.fail();

            // act & assert (예외 없이 실행)
            payment.fail();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }
}
