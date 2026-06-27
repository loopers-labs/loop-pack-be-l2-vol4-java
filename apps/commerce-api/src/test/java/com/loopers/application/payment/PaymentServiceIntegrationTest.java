package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 생성할 때,")
    @Nested
    class CreatePayment {

        @DisplayName("유효한 정보를 주면 PENDING 상태의 Payment가 저장된다.")
        @Test
        void createsPayment_withPendingStatus_whenValidInfoIsProvided() {
            // Arrange & Act
            Payment payment = paymentService.createPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);

            // Assert
            assertAll(
                () -> assertThat(payment.getId()).isNotNull(),
                () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED)
            );
        }
    }

    @DisplayName("PG 결제 요청 성공 후 처리 시작할 때,")
    @Nested
    class StartProcessing {

        @DisplayName("PENDING 상태의 Payment에 transactionKey를 세팅하면 IN_PROGRESS로 전환된다.")
        @Test
        void startsProcessing_setsTransactionKeyAndInProgress() {
            // Arrange
            Payment payment = paymentService.createPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            String transactionKey = "20260622:TR:a1b2c3";

            // Act
            Payment result = paymentService.inProgress(payment, transactionKey);

            // Assert
            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                () -> assertThat(result.getTransactionKey()).isEqualTo(transactionKey)
            );
        }
    }

    @DisplayName("콜백 수신 후 결제를 완료 처리할 때,")
    @Nested
    class Complete {

        @DisplayName("transactionKey로 Payment를 조회하여 SUCCESS로 전환된다.")
        @Test
        void completesPayment_withSuccess() {
            // Arrange
            Payment payment = paymentService.createPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            String transactionKey = "20260622:TR:a1b2c3";
            paymentService.inProgress(payment, transactionKey);

            // Act
            Payment result = paymentService.complete(transactionKey, PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            // Assert
            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(result.getReason()).isEqualTo("정상 승인되었습니다."),
                () -> assertThat(result.getCompletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 transactionKey를 주면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTransactionKeyDoesNotExist() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class,
                () -> paymentService.complete("unknown:TR:000000", PaymentStatus.SUCCESS, "정상 승인되었습니다."));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("중복 결제 여부를 확인할 때,")
    @Nested
    class HasSuccessPayment {

        @DisplayName("SUCCESS 상태의 Payment가 존재하면 true를 반환한다.")
        @Test
        void returnsTrue_whenSuccessPaymentExists() {
            // Arrange
            Payment payment = paymentService.createPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            paymentService.inProgress(payment, "20260622:TR:a1b2c3");
            paymentService.complete("20260622:TR:a1b2c3", PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            // Act & Assert
            assertThat(paymentService.hasSuccessPayment(1L)).isTrue();
        }

        @DisplayName("SUCCESS 상태의 Payment가 없으면 false를 반환한다.")
        @Test
        void returnsFalse_whenNoSuccessPaymentExists() {
            // Act & Assert
            assertThat(paymentService.hasSuccessPayment(1L)).isFalse();
        }
    }

    @DisplayName("transactionKey로 결제를 조회할 때,")
    @Nested
    class GetByTransactionKey {

        @DisplayName("존재하는 transactionKey를 주면 Payment를 반환한다.")
        @Test
        void returnsPayment_whenTransactionKeyExists() {
            Payment payment = paymentService.createPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            String transactionKey = "20260622:TR:a1b2c3";
            paymentService.inProgress(payment, transactionKey);

            Payment result = paymentService.getByTransactionKey(transactionKey);

            assertThat(result.getTransactionKey()).isEqualTo(transactionKey);
        }

        @DisplayName("존재하지 않는 transactionKey를 주면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTransactionKeyDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.getByTransactionKey("unknown:TR:000000"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("PENDING/IN_PROGRESS Payment를 조회할 때,")
    @Nested
    class FindAllPendingOrInProgress {

        @DisplayName("PENDING과 IN_PROGRESS 상태의 Payment 목록을 반환한다.")
        @Test
        void returnsPayments_withPendingOrInProgressStatus() {
            // Arrange
            Payment pending = paymentService.createPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000L);
            Payment inProgress = paymentService.createPayment(1L, 2L, CardType.KB, "9876-5432-1098-7654", 30000L);
            paymentService.inProgress(inProgress, "20260622:TR:a1b2c3");

            Payment completed = paymentService.createPayment(1L, 3L, CardType.HYUNDAI, "1111-2222-3333-4444", 10000L);
            paymentService.inProgress(completed, "20260622:TR:b2c3d4");
            paymentService.complete("20260622:TR:b2c3d4", PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            // Act
            List<Payment> result = paymentService.findAllPendingOrInProgress();

            // Assert
            assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).extracting(Payment::getId)
                    .containsExactlyInAnyOrder(pending.getId(), inProgress.getId())
            );
        }
    }
}
