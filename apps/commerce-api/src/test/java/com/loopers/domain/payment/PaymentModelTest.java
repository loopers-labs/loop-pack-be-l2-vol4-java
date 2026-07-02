package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentModelTest {

    private PaymentModel pending() {
        return PaymentModel.of(1L, 99L, 10000L, CardType.SAMSUNG, "1234-1234-1234-1234");
    }

    @DisplayName("결제를 생성하면")
    @Nested
    class Create {

        @DisplayName("결제 상태는 PENDING, pgRequestAttempted는 false, 그리고 key는 null로 진행한다.")
        @Test
        void startsAsPendingNotAttempted() {
            PaymentModel payment = pending();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.isPgRequestAttempted()).isFalse();
            assertThat(payment.getTransactionKey()).isNull();
        }
    }

    @DisplayName("PG 접수 결과를 반영할 때")
    @Nested
    class MarkRequest {

        @DisplayName("접수 성공이면 transactionKey 가 생성되고 시도 플래그가 true.")
        @Test
        void markRequested_setsKeyAndFlag() {
            PaymentModel payment = pending();

            payment.markRequested("tx-key-1");

            assertThat(payment.getTransactionKey()).isEqualTo("tx-key-1");
            assertThat(payment.isPgRequestAttempted()).isTrue();
        }

        @DisplayName("타임아웃이면 key 는 null 인채로 시도 플래그만 true(역조회 복구 대상).")
        @Test
        void markAttemptedWithoutKey_keepsKeyNull() {
            PaymentModel payment = pending();

            payment.markAttemptedWithoutKey();

            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.isPgRequestAttempted()).isTrue();
        }
    }

    @DisplayName("상태 전이는")
    @Nested
    class Transition {

        @DisplayName("PENDING 일 때만 SUCCESS 로 전이한다.")
        @Test
        void succeed_fromPending() {
            PaymentModel payment = pending();

            payment.succeed();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("PENDING 일 때만 FAILED 로 전이하고 사유를 남긴다.")
        @Test
        void fail_fromPending() {
            PaymentModel payment = pending();

            payment.fail("한도 초과");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("한도 초과");
        }

        @DisplayName("이미 SUCCESS 인데 fail 을 호출하면 무시한다(멱등).")
        @Test
        void fail_ignoredWhenAlreadySuccess() {
            PaymentModel payment = pending();
            payment.succeed();

            payment.fail("뒤늦은 실패 콜백");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getReason()).isNull();
        }

        @DisplayName("이미 FAILED 인데 succeed 를 호출하면 무시한다(멱등).")
        @Test
        void succeed_ignoredWhenAlreadyFailed() {
            PaymentModel payment = pending();
            payment.fail("잘못된 카드");

            payment.succeed();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("잘못된 카드");
        }
    }
}