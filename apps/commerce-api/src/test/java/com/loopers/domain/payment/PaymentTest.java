package com.loopers.domain.payment;

import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 입력이면, PENDING 상태에 트랜잭션 키 없이 생성된다.")
        @Test
        void createsPayment_withPendingStatus() {
            // Arrange & Act
            Payment payment = Payment.create(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

            // Assert
            assertThat(payment.getOrderId()).isEqualTo(1L);
            assertThat(payment.getMemberId()).isEqualTo(2L);
            assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG);
            assertThat(payment.getCardNo()).isEqualTo("1234-5678-9814-1451");
            assertThat(payment.getAmount()).isEqualTo(5000L);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getReason()).isNull();
        }

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Payment.create(null, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Payment.create(1L, null, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardType이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCardTypeIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Payment.create(1L, 2L, null, "1234-5678-9814-1451", 5000L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardNo가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCardNoIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Payment.create(1L, 2L, CardType.SAMSUNG, " ", 5000L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsNotPositive() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Payment.create(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 0L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("트랜잭션 키를 부여할 때, ")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PG 응답으로 받은 트랜잭션 키가 저장된다.")
        @Test
        void assignsTransactionKey() {
            // Arrange
            Payment payment = Payment.create(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

            // Act
            payment.assignTransactionKey("20250816:TR:9577c5");

            // Assert
            assertThat(payment.getTransactionKey()).isEqualTo("20250816:TR:9577c5");
        }
    }

    @DisplayName("결제 결과를 반영할 때, ")
    @Nested
    class MarkResult {

        @DisplayName("PENDING 상태에서 성공 처리하면, SUCCESS 상태와 사유가 저장된다.")
        @Test
        void marksSuccess_fromPending() {
            // Arrange
            Payment payment = Payment.create(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

            // Act
            payment.markSuccess("정상 승인되었습니다.");

            // Assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getReason()).isEqualTo("정상 승인되었습니다.");
        }

        @DisplayName("PENDING 상태에서 실패 처리하면, FAILED 상태와 사유가 저장된다.")
        @Test
        void marksFailed_fromPending() {
            // Arrange
            Payment payment = Payment.create(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

            // Act
            payment.markFailed("한도초과입니다. 다른 카드를 선택해주세요.");

            // Assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("한도초과입니다. 다른 카드를 선택해주세요.");
        }

        @DisplayName("이미 확정된 결제를 다시 처리하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyResolved() {
            // Arrange
            Payment payment = Payment.create(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
            payment.markSuccess("정상 승인되었습니다.");

            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> payment.markFailed("한도초과입니다."));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("PENDING 상태이면 isPending이 true를 반환한다.")
        @Test
        void isPending_returnsTrue_whenPending() {
            Payment payment = Payment.create(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
            assertThat(payment.isPending()).isTrue();
        }
    }
}
