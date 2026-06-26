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
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final Long AMOUNT = 5000L;

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면, PENDING 상태이고 transactionKey는 아직 없다.")
        @Test
        void createsPayment_withPendingStatus_whenValid() {
            PaymentModel payment = new PaymentModel(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, AMOUNT);

            assertAll(
                () -> assertThat(payment.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(payment.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(payment.getAmount()).isEqualTo(AMOUNT),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(payment.getReason()).isNull()
            );
        }

        @DisplayName("카드번호는 마지막 4자리만 남기고 마스킹되어 저장된다.")
        @Test
        void masksCardNo_whenStored() {
            PaymentModel payment = new PaymentModel(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, AMOUNT);

            assertThat(payment.getCardNo()).isEqualTo("****-****-****-1451");
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new PaymentModel(null, ORDER_ID, CardType.SAMSUNG, CARD_NO, AMOUNT));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new PaymentModel(USER_ID, null, CardType.SAMSUNG, CARD_NO, AMOUNT));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardType이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCardTypeNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new PaymentModel(USER_ID, ORDER_ID, null, CARD_NO, AMOUNT));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountNotPositive() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new PaymentModel(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 0L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardNo 형식이 xxxx-xxxx-xxxx-xxxx가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCardNoInvalid() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new PaymentModel(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234", AMOUNT));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    private PaymentModel pendingPayment() {
        return new PaymentModel(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, AMOUNT);
    }

    @DisplayName("PG 트랜잭션 키를 연결할 때, ")
    @Nested
    class LinkTransactionKey {

        @DisplayName("키가 없으면, 전달된 키를 설정한다.")
        @Test
        void setsKey_whenNotLinkedYet() {
            PaymentModel payment = pendingPayment();

            payment.linkTransactionKey("20260623:TR:abc123");

            assertThat(payment.getTransactionKey()).isEqualTo("20260623:TR:abc123");
        }

        @DisplayName("같은 키로 다시 연결하면, 멱등하게 동일 상태를 유지한다.")
        @Test
        void isIdempotent_whenSameKey() {
            PaymentModel payment = pendingPayment();
            payment.linkTransactionKey("20260623:TR:abc123");

            payment.linkTransactionKey("20260623:TR:abc123");

            assertThat(payment.getTransactionKey()).isEqualTo("20260623:TR:abc123");
        }

        @DisplayName("이미 다른 키가 연결돼 있으면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenDifferentKey() {
            PaymentModel payment = pendingPayment();
            payment.linkTransactionKey("20260623:TR:abc123");

            CoreException ex = assertThrows(CoreException.class, () ->
                payment.linkTransactionKey("20260623:TR:xxxxxx"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결제를 성공 처리할 때, ")
    @Nested
    class MarkSuccess {

        @DisplayName("PENDING이면, SUCCESS로 전이하고 사유를 기록한다.")
        @Test
        void transitionsToSuccess_whenPending() {
            PaymentModel payment = pendingPayment();

            payment.markSuccess("정상 승인되었습니다.");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getReason()).isEqualTo("정상 승인되었습니다.")
            );
        }

        @DisplayName("이미 SUCCESS이면, 멱등하게 무시한다. (중복 콜백 방어)")
        @Test
        void isIdempotent_whenAlreadySuccess() {
            PaymentModel payment = pendingPayment();
            payment.markSuccess("정상 승인되었습니다.");

            payment.markSuccess("두 번째 콜백");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getReason()).isEqualTo("정상 승인되었습니다.")
            );
        }

        @DisplayName("이미 FAILED이면, CONFLICT 예외가 발생한다. (역전 방지)")
        @Test
        void throwsConflict_whenAlreadyFailed() {
            PaymentModel payment = pendingPayment();
            payment.markFailed("한도초과입니다.");

            CoreException ex = assertThrows(CoreException.class, () ->
                payment.markSuccess("뒤늦은 성공 콜백"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결제를 실패 처리할 때, ")
    @Nested
    class MarkFailed {

        @DisplayName("PENDING이면, FAILED로 전이하고 사유를 기록한다.")
        @Test
        void transitionsToFailed_whenPending() {
            PaymentModel payment = pendingPayment();

            payment.markFailed("한도초과입니다.");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도초과입니다.")
            );
        }

        @DisplayName("이미 FAILED이면, 멱등하게 무시한다.")
        @Test
        void isIdempotent_whenAlreadyFailed() {
            PaymentModel payment = pendingPayment();
            payment.markFailed("한도초과입니다.");

            payment.markFailed("잘못된 카드입니다.");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도초과입니다.")
            );
        }

        @DisplayName("이미 SUCCESS이면, CONFLICT 예외가 발생한다. (역전 방지)")
        @Test
        void throwsConflict_whenAlreadySuccess() {
            PaymentModel payment = pendingPayment();
            payment.markSuccess("정상 승인되었습니다.");

            CoreException ex = assertThrows(CoreException.class, () ->
                payment.markFailed("뒤늦은 실패 콜백"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
