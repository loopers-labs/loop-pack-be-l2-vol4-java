package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentModelTest {

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면, PENDING 상태로 생성된다.")
        @Test
        void createsPayment_whenValidInput() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            assertThat(payment.getOrderId()).isEqualTo(1L);
            assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG);
            assertThat(payment.getAmount()).isEqualTo(10000L);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
        }

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenOrderIdIsNull() {
            assertThatThrownBy(() -> new PaymentModel(null, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenAmountIsZeroOrNegative() {
            assertThatThrownBy(() -> new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 0L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제를 성공 처리할 때,")
    @Nested
    class Success {

        @DisplayName("PENDING 상태에서 success()를 호출하면, SUCCESS 상태로 변경되고 transactionKey가 저장된다.")
        @Test
        void success_changeStatusToSuccess() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            payment.success("20250625:TR:abc123");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionKey()).isEqualTo("20250625:TR:abc123");
        }

        @DisplayName("이미 SUCCESS 상태에서 success()를 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadySuccess() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.success("20250625:TR:abc123");

            assertThatThrownBy(() -> payment.success("20250625:TR:abc123"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("FAILED 상태에서 success()를 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyFailed() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.fail(null, "서버 오류");

            assertThatThrownBy(() -> payment.success("20250625:TR:abc123"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("transactionKey를 할당할 때,")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PENDING 상태에서 assignTransactionKey()를 호출하면, transactionKey가 저장되고 상태는 유지된다.")
        @Test
        void assignTransactionKey_savesKey_andKeepsPending() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            // act
            payment.assignTransactionKey("20250625:TR:abc123");

            // assert
            assertThat(payment.getTransactionKey()).isEqualTo("20250625:TR:abc123");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("PENDING 이외 상태에서 assignTransactionKey()를 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void assignTransactionKey_throwsException_whenNotPending() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.success("20250625:TR:abc123");

            // act & assert
            assertThatThrownBy(() -> payment.assignTransactionKey("20250625:TR:another"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제를 실패 처리할 때,")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태에서 transactionKey와 함께 fail()을 호출하면, FAILED 상태로 변경되고 transactionKey와 reason이 저장된다.")
        @Test
        void fail_withTransactionKey_changeStatusToFailed() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            payment.fail("20250625:TR:abc123", "한도 초과");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getTransactionKey()).isEqualTo("20250625:TR:abc123");
            assertThat(payment.getFailureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("PENDING 상태에서 transactionKey 없이 fail()을 호출하면, FAILED 상태로 변경되고 reason만 저장된다.")
        @Test
        void fail_withoutTransactionKey_changeStatusToFailed() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            payment.fail(null, "PG 서버 오류");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getFailureReason()).isEqualTo("PG 서버 오류");
        }

        @DisplayName("이미 FAILED 상태에서 fail()을 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyFailed() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.fail(null, "PG 서버 오류");

            assertThatThrownBy(() -> payment.fail(null, "PG 서버 오류"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("SUCCESS 상태에서 fail()을 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadySuccess() {
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.success("20250625:TR:abc123");

            assertThatThrownBy(() -> payment.fail(null, "오류"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
