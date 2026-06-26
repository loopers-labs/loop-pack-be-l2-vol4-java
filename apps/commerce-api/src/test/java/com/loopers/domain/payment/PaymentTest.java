package com.loopers.domain.payment;

import com.loopers.domain.money.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    private Payment pendingPayment() {
        return new Payment(1L, 1L, "tx-001",
            new Money(BigDecimal.valueOf(1000)), PaymentStatus.PENDING, null);
    }

    @DisplayName("결제 결과를 확정할 때, ")
    @Nested
    class Confirm {
        @DisplayName("markSuccess 는 PENDING 결제를 SUCCESS 로 전이한다.")
        @Test
        void marksSuccess_whenPending() {
            // arrange
            Payment payment = pendingPayment();

            // act
            payment.markSuccess();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("markFailed 는 PENDING 결제를 FAILED 로 전이하고 사유를 기록한다.")
        @Test
        void marksFailed_withReason_whenPending() {
            // arrange
            Payment payment = pendingPayment();

            // act
            payment.markFailed("카드 한도 초과");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("카드 한도 초과");
        }

        @DisplayName("이미 확정된 결제를 다시 전이하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyDecided() {
            // arrange
            Payment payment = pendingPayment();
            payment.markSuccess();

            // act
            CoreException result = assertThrows(CoreException.class, payment::markSuccess);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
