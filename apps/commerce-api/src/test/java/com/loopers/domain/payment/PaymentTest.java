package com.loopers.domain.payment;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1001L;

    private Payment pending() {
        return Payment.pending(USER_ID, ORDER_ID, Money.of(5000L), CardType.SAMSUNG);
    }

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 상태는 PENDING 이고 거래키는 아직 없다.")
        @Test
        void createsPending_whenValid() {
            // act
            Payment payment = Payment.pending(USER_ID, ORDER_ID, Money.of(5000L), CardType.SAMSUNG);

            // assert
            assertAll(
                () -> assertThat(payment.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(payment.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(payment.getAmount()).isEqualTo(5000L),
                () -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getTransactionKey()).isNull()
            );
        }

        @DisplayName("유저/주문/금액/카드종류가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            Money amount = Money.of(5000L);
            assertAll(
                () -> assertThat(assertThrows(CoreException.class,
                    () -> Payment.pending(null, ORDER_ID, amount, CardType.SAMSUNG)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class,
                    () -> Payment.pending(USER_ID, null, amount, CardType.SAMSUNG)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class,
                    () -> Payment.pending(USER_ID, ORDER_ID, null, CardType.SAMSUNG)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class,
                    () -> Payment.pending(USER_ID, ORDER_ID, amount, null)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("거래키를 부여할 때, ")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PENDING 상태에서 거래키를 부여하면, 키가 저장되고 상태는 그대로 PENDING 이다.")
        @Test
        void assigns_whenPending() {
            // arrange
            Payment payment = pending();

            // act
            payment.assignTransactionKey("20260625:TR:abc123");

            // assert
            assertAll(
                () -> assertThat(payment.getTransactionKey()).isEqualTo("20260625:TR:abc123"),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING)
            );
        }
    }

    @DisplayName("결제 결과를 반영할 때, ")
    @Nested
    class Complete {

        @DisplayName("PENDING 에서 성공 처리하면, 상태가 SUCCESS 가 된다.")
        @Test
        void markSuccess_fromPending() {
            // arrange
            Payment payment = pending();

            // act
            payment.markSuccess();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("PENDING 에서 실패 처리하면, 상태가 FAILED 가 되고 사유가 기록된다.")
        @Test
        void markFailed_fromPending() {
            // arrange
            Payment payment = pending();

            // act
            payment.markFailed("한도 초과");

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도 초과")
            );
        }

        @DisplayName("이미 터미널(SUCCESS) 상태면, 다시 성공/실패 처리 시 BAD_REQUEST 예외가 발생한다. (터미널 가드)")
        @Test
        void throwsBadRequest_whenAlreadyTerminal() {
            // arrange
            Payment payment = pending();
            payment.markSuccess();

            // act + assert
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, payment::markSuccess).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> payment.markFailed("x")).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }
}
