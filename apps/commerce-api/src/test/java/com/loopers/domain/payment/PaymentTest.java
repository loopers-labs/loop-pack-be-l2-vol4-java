package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Payment 도메인 단위 테스트")
class PaymentTest {

    @Nested
    @DisplayName("Payment 생성 시")
    class Create {

        @Test
        @DisplayName("유효한 정보가 주어지면 PENDING 상태의 Payment가 생성된다.")
        void createsPayment_withPendingStatus_whenValidInfoIsProvided() {
            // Arrange
            Long userId = 1L;
            Long orderId = 1L;
            CardType cardType = CardType.SAMSUNG;
            String cardNo = "1234-5678-9012-3456";
            Long amount = 50000L;

            // Act
            Payment payment = new Payment(userId, orderId, cardType, cardNo, amount);

            // Assert
            assertAll(
                () -> assertThat(payment.getUserId()).isEqualTo(userId),
                () -> assertThat(payment.getOrderId()).isEqualTo(orderId),
                () -> assertThat(payment.getCardType()).isEqualTo(cardType),
                () -> assertThat(payment.getCardNo()).isEqualTo(cardNo),
                () -> assertThat(payment.getAmount()).isEqualTo(amount),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED),
                () -> assertThat(payment.getReason()).isNull(),
                () -> assertThat(payment.getPollingCount()).isZero(),
                () -> assertThat(payment.getLastPolledAt()).isNull(),
                () -> assertThat(payment.getCompletedAt()).isNull()
            );
        }
    }

    @Nested
    @DisplayName("PG 결제 요청 성공 시")
    class StartProcessing {

        @Test
        @DisplayName("PENDING 상태에서 markInProgress을 호출하면 IN_PROGRESS로 전환되고 transactionKey가 세팅된다.")
        void startsProcessing_whenStatusIsPending() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            String transactionKey = "20260622:TR:a1b2c3";

            // Act
            payment.markInProgress(transactionKey);

            // Assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                () -> assertThat(payment.getTransactionKey()).isEqualTo(transactionKey)
            );
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 markInProgress을 호출하면 예외가 발생한다.")
        void throwsException_whenStartProcessingCalledOnNonPendingStatus() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class,
                () -> payment.markInProgress("20260622:TR:b2c3d4"));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @Nested
    @DisplayName("결제 완료 처리 시")
    class Complete {

        @Test
        @DisplayName("IN_PROGRESS 상태에서 SUCCESS 콜백을 수신하면 SUCCESS로 전환된다.")
        void completesPayment_withSuccess_whenCallbackIsSuccess() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");
            String reason = "정상 승인되었습니다.";

            // Act
            payment.complete(PaymentStatus.SUCCESS, reason);

            // Assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getReason()).isEqualTo(reason),
                () -> assertThat(payment.getCompletedAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 FAILED 콜백을 수신하면 FAILED로 전환된다.")
        void completesPayment_withFailed_whenCallbackIsFailed() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");
            String reason = "한도초과입니다.";

            // Act
            payment.complete(PaymentStatus.FAILED, reason);

            // Assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo(reason),
                () -> assertThat(payment.getCompletedAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("POLLING_EXHAUSTED 상태에서 SUCCESS 콜백을 수신하면 SUCCESS로 전환된다.")
        void completesPayment_withSuccess_whenStatusIsPollingExhausted() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");
            payment.exhaustPolling();
            String reason = "정상 승인되었습니다.";

            // Act
            payment.complete(PaymentStatus.SUCCESS, reason);

            // Assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getReason()).isEqualTo(reason),
                () -> assertThat(payment.getCompletedAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("PENDING 상태에서 complete를 호출하면 예외가 발생한다.")
        void throwsException_whenCompleteCalledOnPendingStatus() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class,
                () -> payment.complete(PaymentStatus.SUCCESS, "정상 승인되었습니다."));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @Nested
    @DisplayName("폴링 소진 처리 시")
    class ExhaustPolling {

        @Test
        @DisplayName("CREATED 상태에서 exhaustPolling을 호출하면 POLLING_EXHAUSTED로 전환된다.")
        void exhaustsPolling_whenStatusIsCreated() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);

            // Act
            payment.exhaustPolling();

            // Assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.POLLING_EXHAUSTED);
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 exhaustPolling을 호출하면 POLLING_EXHAUSTED로 전환된다.")
        void exhaustsPolling_whenStatusIsInProgress() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");

            // Act
            payment.exhaustPolling();

            // Assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.POLLING_EXHAUSTED);
        }

        @Test
        @DisplayName("SUCCESS 상태에서 exhaustPolling을 호출하면 예외가 발생한다.")
        void throwsException_whenExhaustPollingCalledOnSuccessStatus() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");
            payment.complete(PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, payment::exhaustPolling);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @Nested
    @DisplayName("폴링 기록 시")
    class RecordPolling {

        @Test
        @DisplayName("recordPolling을 호출하면 폴링 횟수가 증가하고 마지막 조회 시각이 갱신된다.")
        void recordsPolling_incrementsCountAndUpdatesLastPolledAt() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);

            // Act
            payment.recordPolling();

            // Assert
            assertAll(
                () -> assertThat(payment.getPollingCount()).isEqualTo(1),
                () -> assertThat(payment.getLastPolledAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("SUCCESS 상태에서 recordPolling을 호출하면 예외가 발생한다.")
        void throwsException_whenRecordPollingCalledOnSuccessStatus() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");
            payment.complete(PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, payment::recordPolling);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @Test
        @DisplayName("FAILED 상태에서 recordPolling을 호출하면 예외가 발생한다.")
        void throwsException_whenRecordPollingCalledOnFailedStatus() {
            // Arrange
            Payment payment = new Payment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            payment.markInProgress("20260622:TR:a1b2c3");
            payment.complete(PaymentStatus.FAILED, "한도초과입니다.");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, payment::recordPolling);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}