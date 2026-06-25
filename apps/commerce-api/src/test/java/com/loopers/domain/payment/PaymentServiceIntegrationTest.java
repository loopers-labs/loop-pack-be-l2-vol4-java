package com.loopers.domain.payment;

import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
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

    @DisplayName("결제를 시작할 때, ")
    @Nested
    class Initiate {

        @DisplayName("정상 입력이면, PENDING 결제가 저장된다.")
        @Test
        void savesPendingPayment() {
            // Act
            Payment payment = paymentService.initiate(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

            // Assert
            assertThat(payment.getId()).isNotNull();
            assertThat(payment.getOrderId()).isEqualTo(1L);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("같은 주문에 진행 중인 결제가 있으면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenActivePaymentExists() {
            // Arrange
            paymentService.initiate(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.initiate(1L, 2L, CardType.KB, "1234-5678-9814-1452", 5000L));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("트랜잭션 키를 부여할 때, ")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PG 응답으로 받은 트랜잭션 키가 저장된다.")
        @Test
        void assignsTransactionKey() {
            // Arrange
            Payment payment = paymentService.initiate(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

            // Act
            paymentService.assignTransactionKey(payment.getId(), "20250816:TR:9577c5");

            // Assert
            Payment found = paymentService.getPayment(payment.getId());
            assertThat(found.getTransactionKey()).isEqualTo("20250816:TR:9577c5");
        }
    }

    @DisplayName("PG 결과를 반영할 때, ")
    @Nested
    class ConfirmResult {

        private Payment pendingPaymentWithKey(String transactionKey) {
            Payment payment = paymentService.initiate(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
            paymentService.assignTransactionKey(payment.getId(), transactionKey);
            return payment;
        }

        @DisplayName("성공 결과면, 결제가 SUCCESS로 확정된다.")
        @Test
        void confirmsSuccess() {
            // Arrange
            Payment payment = pendingPaymentWithKey("TR:success");

            // Act
            paymentService.confirmResult("TR:success", PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            // Assert
            assertThat(paymentService.getPayment(payment.getId()).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("실패 결과면, 결제가 FAILED로 확정되고 사유가 저장된다.")
        @Test
        void confirmsFailed() {
            // Arrange
            Payment payment = pendingPaymentWithKey("TR:failed");

            // Act
            paymentService.confirmResult("TR:failed", PaymentStatus.FAILED, "한도초과입니다.");

            // Assert
            Payment found = paymentService.getPayment(payment.getId());
            assertThat(found.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(found.getReason()).isEqualTo("한도초과입니다.");
        }

        @DisplayName("이미 확정된 결제에 중복 결과가 와도, 무시되어 상태가 유지된다.(멱등)")
        @Test
        void ignoresDuplicate() {
            // Arrange
            Payment payment = pendingPaymentWithKey("TR:dup");
            paymentService.confirmResult("TR:dup", PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            // Act (콜백/폴링 중복 도착 가정)
            paymentService.confirmResult("TR:dup", PaymentStatus.FAILED, "한도초과입니다.");

            // Assert
            assertThat(paymentService.getPayment(payment.getId()).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("존재하지 않는 트랜잭션 키면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenKeyMissing() {
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.confirmResult("TR:none", PaymentStatus.SUCCESS, "정상 승인되었습니다."));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
