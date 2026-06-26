package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Payment 상태머신 단위 테스트. 멱등성 / 잘못된 전이 차단 / 안전성을 중심으로 검증한다.
 * 통합 동작(콜백·폴링과의 race) 은 상위 레이어 통합 테스트에서 다룬다.
 */
class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Money AMOUNT = Money.of(5_000L);
    private static final String TX_KEY = "20260624:TR:abc123";

    private Payment newRequested() {
        return Payment.request(ORDER_ID, USER_ID, PgProvider.PG_SIMULATOR, AMOUNT, CardType.SAMSUNG, "1451");
    }

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 인자로 생성하면 status 는 REQUESTED 이며 transactionKey 는 null 이다.")
        @Test
        void createsAsRequested() {
            // act
            Payment payment = newRequested();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getCompletedAt()).isNull();
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getCardLastFour()).isEqualTo("1451");
        }

        @DisplayName("amount 가 0 이면 BAD_REQUEST 가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsZero() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Payment.request(ORDER_ID, USER_ID, PgProvider.PG_SIMULATOR, Money.zero(), CardType.SAMSUNG, "1451"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardLastFour 가 4자가 아니면 BAD_REQUEST 가 발생한다.")
        @Test
        void throwsBadRequest_whenCardLastFourIsInvalid() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Payment.request(ORDER_ID, USER_ID, PgProvider.PG_SIMULATOR, AMOUNT, CardType.SAMSUNG, "12"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("markInProgress 호출 시, ")
    @Nested
    class MarkInProgress {

        @DisplayName("REQUESTED 에서 호출하면 IN_PROGRESS 로 전이되고 transactionKey 가 저장된다.")
        @Test
        void transitionsToInProgress() {
            // arrange
            Payment payment = newRequested();

            // act
            payment.markInProgress(TX_KEY);

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
            assertThat(payment.getTransactionKey()).isEqualTo(TX_KEY);
        }

        @DisplayName("같은 transactionKey 로 재호출하면 멱등하게 동작한다 (예외 없음).")
        @Test
        void isIdempotent_onSameTransactionKey() {
            Payment payment = newRequested();
            payment.markInProgress(TX_KEY);

            payment.markInProgress(TX_KEY);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
            assertThat(payment.getTransactionKey()).isEqualTo(TX_KEY);
        }

        @DisplayName("IN_PROGRESS 에서 다른 transactionKey 로 호출하면 CONFLICT 가 발생한다.")
        @Test
        void throwsConflict_onDifferentTransactionKey() {
            Payment payment = newRequested();
            payment.markInProgress(TX_KEY);

            CoreException ex = assertThrows(CoreException.class,
                () -> payment.markInProgress("OTHER:KEY"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("transactionKey 가 빈 문자열이면 BAD_REQUEST 가 발생한다.")
        @Test
        void throwsBadRequest_whenKeyIsBlank() {
            Payment payment = newRequested();

            CoreException ex = assertThrows(CoreException.class,
                () -> payment.markInProgress(" "));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("markSuccess 호출 시, ")
    @Nested
    class MarkSuccess {

        @DisplayName("IN_PROGRESS 에서 호출하면 SUCCESS 로 전이되고 completedAt 이 채워진다.")
        @Test
        void transitionsToSuccess_fromInProgress() {
            Payment payment = newRequested();
            payment.markInProgress(TX_KEY);

            payment.markSuccess();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getCompletedAt()).isNotNull();
            assertThat(payment.getReason()).isNotBlank();
        }

        @DisplayName("UNKNOWN 에서 호출하면 SUCCESS 로 전이된다 (폴링이 확정한 케이스).")
        @Test
        void transitionsToSuccess_fromUnknown() {
            Payment payment = newRequested();
            payment.markUnknown("PG 호출 timeout");

            payment.markSuccess();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("이미 SUCCESS 면 멱등하게 동작한다 (예외 없음).")
        @Test
        void isIdempotent_whenAlreadySuccess() {
            Payment payment = newRequested();
            payment.markInProgress(TX_KEY);
            payment.markSuccess();

            payment.markSuccess();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("FAILED 상태에서 SUCCESS 로 전이는 차단되어 CONFLICT 가 발생한다 (안전성).")
        @Test
        void throwsConflict_whenFailedToSuccess() {
            Payment payment = newRequested();
            payment.markFailed("한도 초과");

            CoreException ex = assertThrows(CoreException.class, payment::markSuccess);
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("REQUESTED 에서 호출하면 CONFLICT 가 발생한다 (IN_PROGRESS 거치지 않은 SUCCESS 금지).")
        @Test
        void throwsConflict_whenRequestedToSuccess() {
            Payment payment = newRequested();

            CoreException ex = assertThrows(CoreException.class, payment::markSuccess);
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("markFailed 호출 시, ")
    @Nested
    class MarkFailed {

        @DisplayName("REQUESTED 에서 호출하면 FAILED 로 전이된다 (PG 영구 에러 / 라우팅 실패 케이스).")
        @Test
        void transitionsToFailed_fromRequested() {
            Payment payment = newRequested();

            payment.markFailed("PG 4xx — 유효하지 않은 카드");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("PG 4xx — 유효하지 않은 카드");
        }

        @DisplayName("IN_PROGRESS / UNKNOWN 에서 모두 FAILED 로 전이된다.")
        @Test
        void transitionsToFailed_fromInProgressAndUnknown() {
            Payment a = newRequested();
            a.markInProgress(TX_KEY);
            a.markFailed("한도 초과");
            assertThat(a.getStatus()).isEqualTo(PaymentStatus.FAILED);

            Payment b = newRequested();
            b.markUnknown("PG 호출 timeout");
            b.markFailed("타임아웃 만료");
            assertThat(b.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("이미 FAILED 면 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenAlreadyFailed() {
            Payment payment = newRequested();
            payment.markFailed("first reason");

            payment.markFailed("second reason");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("first reason"); // 이후 호출은 무시
        }

        @DisplayName("SUCCESS 상태에서 FAILED 로 전이는 차단되어 CONFLICT 가 발생한다.")
        @Test
        void throwsConflict_whenSuccessToFailed() {
            Payment payment = newRequested();
            payment.markInProgress(TX_KEY);
            payment.markSuccess();

            CoreException ex = assertThrows(CoreException.class,
                () -> payment.markFailed("늦은 실패"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("markUnknown 호출 시, ")
    @Nested
    class MarkUnknown {

        @DisplayName("REQUESTED 에서 호출하면 UNKNOWN 으로 전이된다.")
        @Test
        void transitionsToUnknown_fromRequested() {
            Payment payment = newRequested();

            payment.markUnknown("PG 호출 timeout");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
            assertThat(payment.getReason()).isEqualTo("PG 호출 timeout");
        }

        @DisplayName("이미 UNKNOWN 이면 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenAlreadyUnknown() {
            Payment payment = newRequested();
            payment.markUnknown("first reason");

            payment.markUnknown("second reason");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        }

        @DisplayName("IN_PROGRESS 에서 호출하면 CONFLICT 가 발생한다 (상태 후퇴 금지).")
        @Test
        void throwsConflict_fromInProgress() {
            Payment payment = newRequested();
            payment.markInProgress(TX_KEY);

            CoreException ex = assertThrows(CoreException.class,
                () -> payment.markUnknown("뒤늦은 timeout"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
