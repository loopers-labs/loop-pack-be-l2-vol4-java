package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    private static final String CARD_NO = "1234-5678-9012-3456";

    private static PaymentModel pending() {
        return new PaymentModel(10L, 20L, 5000L, CardType.SAMSUNG, CARD_NO);
    }

    @DisplayName("생성")
    @Nested
    class Construct {

        @DisplayName("정상 생성 시 status 는 PENDING 이고 transactionKey 는 null 이다.")
        @Test
        void createsInPending() {
            PaymentModel payment = pending();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
        }

        @DisplayName("카드 번호 형식이 xxxx-xxxx-xxxx-xxxx 가 아니면 BAD_REQUEST.")
        @Test
        void rejectsBadCardNo() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new PaymentModel(10L, 20L, 5000L, CardType.SAMSUNG, "1234567890123456"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("금액이 음수면 BAD_REQUEST.")
        @Test
        void rejectsNegativeAmount() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new PaymentModel(10L, 20L, -1L, CardType.SAMSUNG, CARD_NO));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상태 전이")
    @Nested
    class Transition {

        @DisplayName("PENDING → REQUESTED 시 transactionKey 가 채워진다.")
        @Test
        void markRequested() {
            PaymentModel payment = pending();

            payment.markRequested("TX-001");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getTransactionKey()).isEqualTo("TX-001");
        }

        @DisplayName("REQUESTED 상태에서 markRequested 재호출은 CONFLICT.")
        @Test
        void cannotRequestTwice() {
            PaymentModel payment = pending();
            payment.markRequested("TX-001");

            CoreException ex = assertThrows(CoreException.class, () -> payment.markRequested("TX-002"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("PENDING → TIMEOUT_PENDING 정상 전이.")
        @Test
        void markTimeoutPending() {
            PaymentModel payment = pending();

            payment.markTimeoutPending("PG 응답 지연");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.TIMEOUT_PENDING);
            assertThat(payment.getFailReason()).isEqualTo("PG 응답 지연");
        }

        @DisplayName("REQUESTED → SUCCEEDED 정상 전이.")
        @Test
        void requestedSucceeds() {
            PaymentModel payment = pending();
            payment.markRequested("TX");

            payment.markSucceeded();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @DisplayName("이미 SUCCEEDED 인 결제에 markSucceeded 재호출은 멱등 통과.")
        @Test
        void succeededIsIdempotent() {
            PaymentModel payment = pending();
            payment.markRequested("TX");
            payment.markSucceeded();

            payment.markSucceeded();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @DisplayName("SUCCEEDED → FAILED 시도는 CONFLICT (PG 모순).")
        @Test
        void cannotFailAfterSucceed() {
            PaymentModel payment = pending();
            payment.markRequested("TX");
            payment.markSucceeded();

            CoreException ex = assertThrows(CoreException.class, () -> payment.markFailed("뒤늦은 거절"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("TIMEOUT_PENDING → SUCCEEDED 가능 (복구 폴링이 성공 확정).")
        @Test
        void timeoutCanSucceed() {
            PaymentModel payment = pending();
            payment.markTimeoutPending("타임아웃");

            payment.markSucceeded();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @DisplayName("TIMEOUT_PENDING 상태에서 transactionKey attach 가능, 이미 있으면 멱등.")
        @Test
        void attachTransactionKey() {
            PaymentModel payment = pending();
            payment.markTimeoutPending("타임아웃");

            payment.attachTransactionKey("TX-Recovered");
            payment.attachTransactionKey("TX-Other"); // 멱등

            assertThat(payment.getTransactionKey()).isEqualTo("TX-Recovered");
        }
    }
}
