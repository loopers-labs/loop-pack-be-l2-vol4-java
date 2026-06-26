package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentStatusTest {

    @DisplayName("종료 상태 여부를 판별할 때,")
    @Nested
    class IsTerminal {

        @DisplayName("PAID·FAILED 는 종료 상태다.")
        @Test
        void returnsTrue_forPaidAndFailed() {
            assertAll(
                    () -> assertThat(PaymentStatus.PAID.isTerminal()).isTrue(),
                    () -> assertThat(PaymentStatus.FAILED.isTerminal()).isTrue()
            );
        }

        @DisplayName("PENDING·UNKNOWN 은 종료 상태가 아니다.")
        @Test
        void returnsFalse_forPendingAndUnknown() {
            assertAll(
                    () -> assertThat(PaymentStatus.PENDING.isTerminal()).isFalse(),
                    () -> assertThat(PaymentStatus.UNKNOWN.isTerminal()).isFalse()
            );
        }
    }
}
