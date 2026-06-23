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

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long AMOUNT = 50_000L;

    @DisplayName("결제를 PENDING 으로 생성할 때, ")
    @Nested
    class CreatePending {

        @DisplayName("유효한 값이 주어지면, PENDING 상태로 생성되고 transactionKey/reason 은 비어있다.")
        @Test
        void createsPendingPayment_whenValid() {
            // given
            // when
            PaymentModel payment = PaymentModel.createPending(USER_ID, ORDER_ID, AMOUNT);

            // then
            assertAll(
                () -> assertThat(payment.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(payment.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(payment.getAmount()).isEqualTo(AMOUNT),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(payment.getReason()).isNull()
            );
        }

        @DisplayName("금액이 0 이하이면, INVALID_PAYMENT_AMOUNT 예외가 발생한다.")
        @Test
        void throwsInvalidPaymentAmount_whenAmountIsNotPositive() {
            // given
            Long amount = 0L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> PaymentModel.createPending(USER_ID, ORDER_ID, amount));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PAYMENT_AMOUNT);
        }

        @DisplayName("userId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // given
            Long userId = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> PaymentModel.createPending(userId, ORDER_ID, AMOUNT));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("orderId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            // given
            Long orderId = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> PaymentModel.createPending(USER_ID, orderId, AMOUNT));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("transactionKey 를 부여할 때, ")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PENDING 이고 키가 아직 없으면, 키가 부여된다.")
        @Test
        void assignsKey_whenPendingAndKeyIsAbsent() {
            // given
            PaymentModel payment = pendingPayment();

            // when
            payment.assignTransactionKey("20260623:TR:abc123");

            // then
            assertThat(payment.getTransactionKey()).isEqualTo("20260623:TR:abc123");
        }

        @DisplayName("이미 키가 있으면, 재부여하지 않고 기존 키를 유지한다.")
        @Test
        void keepsOriginalKey_whenKeyAlreadyAssigned() {
            // given
            PaymentModel payment = pendingPayment();
            payment.assignTransactionKey("20260623:TR:first");

            // when
            payment.assignTransactionKey("20260623:TR:second");

            // then
            assertThat(payment.getTransactionKey()).isEqualTo("20260623:TR:first");
        }

        @DisplayName("이미 종착 상태(SUCCESS)이면, 키를 부여하지 않는다.")
        @Test
        void doesNotAssignKey_whenAlreadyTerminal() {
            // given
            PaymentModel payment = pendingPayment();
            payment.markSuccess("정상 승인되었습니다.");

            // when
            payment.assignTransactionKey("20260623:TR:late");

            // then
            assertThat(payment.getTransactionKey()).isNull();
        }
    }

    @DisplayName("결제 성공을 반영할 때, ")
    @Nested
    class MarkSuccess {

        @DisplayName("PENDING 상태이면, SUCCESS 로 전이되고 reason 이 기록된다.")
        @Test
        void transitionsToSuccess_whenPending() {
            // given
            PaymentModel payment = pendingPayment();

            // when
            payment.markSuccess("정상 승인되었습니다.");

            // then
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getReason()).isEqualTo("정상 승인되었습니다.")
            );
        }

        @DisplayName("이미 SUCCESS 상태에서 다시 호출되면(중복 수렴), 예외 없이 SUCCESS 를 유지한다.")
        @Test
        void staysSuccess_whenAlreadySuccess() {
            // given
            PaymentModel payment = pendingPayment();
            payment.markSuccess("정상 승인되었습니다.");

            // when
            payment.markSuccess("정상 승인되었습니다.");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("이미 FAILED 상태이면, SUCCESS 로 역전되지 않고 FAILED 를 유지한다.")
        @Test
        void doesNotFlipFailedToSuccess() {
            // given
            PaymentModel payment = pendingPayment();
            payment.markFailed("한도초과입니다.");

            // when
            payment.markSuccess("정상 승인되었습니다.");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @DisplayName("결제 실패를 반영할 때, ")
    @Nested
    class MarkFailed {

        @DisplayName("PENDING 상태이면, FAILED 로 전이되고 reason 이 기록된다.")
        @Test
        void transitionsToFailed_whenPending() {
            // given
            PaymentModel payment = pendingPayment();

            // when
            payment.markFailed("한도초과입니다. 다른 카드를 선택해주세요.");

            // then
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도초과입니다. 다른 카드를 선택해주세요.")
            );
        }

        @DisplayName("이미 FAILED 상태에서 다시 호출되면(중복 수렴), 예외 없이 FAILED 를 유지한다.")
        @Test
        void staysFailed_whenAlreadyFailed() {
            // given
            PaymentModel payment = pendingPayment();
            payment.markFailed("한도초과입니다.");

            // when
            payment.markFailed("한도초과입니다.");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("이미 SUCCESS 상태이면, FAILED 로 역전되지 않고 SUCCESS 를 유지한다.")
        @Test
        void doesNotFlipSuccessToFailed() {
            // given
            PaymentModel payment = pendingPayment();
            payment.markSuccess("정상 승인되었습니다.");

            // when
            payment.markFailed("한도초과입니다.");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }

    private PaymentModel pendingPayment() {
        return PaymentModel.createPending(USER_ID, ORDER_ID, AMOUNT);
    }
}
