package com.loopers.domain.payment;

import com.loopers.domain.order.vo.Money;
import com.loopers.domain.payment.enums.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    private PaymentModel createPayment() {
        return new PaymentModel(1L, new Money(10000L));
    }

    @DisplayName("결제 승인 시,")
    @Nested
    class Approve {

        @DisplayName("결제 대기 상태면, 승인 상태로 변경된다.")
        @Test
        void approvesPayment_whenStatusIsPending() {
            PaymentModel payment = createPayment();

            payment.approve();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        }

        @DisplayName("결제 대기 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotPending() {
            PaymentModel payment = createPayment();
            payment.approve();

            CoreException exception = assertThrows(CoreException.class, payment::approve);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 실패 처리 시,")
    @Nested
    class Fail {

        @DisplayName("결제 대기 상태면, 실패 상태로 변경된다.")
        @Test
        void failsPayment_whenStatusIsPending() {
            PaymentModel payment = createPayment();

            payment.fail();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("결제 대기 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotPending() {
            PaymentModel payment = createPayment();
            payment.fail();

            CoreException exception = assertThrows(CoreException.class, payment::fail);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 만료 처리 시,")
    @Nested
    class Expire {

        @DisplayName("결제 대기 상태면, 만료 상태로 변경된다.")
        @Test
        void expiresPayment_whenStatusIsPending() {
            PaymentModel payment = createPayment();

            payment.expire();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }

        @DisplayName("결제 대기 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotPending() {
            PaymentModel payment = createPayment();
            payment.expire();

            CoreException exception = assertThrows(CoreException.class, payment::expire);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
