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
}
