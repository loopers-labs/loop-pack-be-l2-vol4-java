package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면, PENDING 상태로 저장된다.")
        @Test
        void create_savesPaymentAsPending() {
            // act
            PaymentModel payment = paymentService.create(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            // assert
            assertThat(payment.getId()).isPositive();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
        }
    }

    @DisplayName("transactionKey를 저장할 때,")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PENDING 결제에 transactionKey를 저장하면, transactionKey가 저장되고 PENDING 상태를 유지한다.")
        @Test
        void assignTransactionKey_savesKeyAndKeepsPending() {
            // arrange
            paymentJpaRepository.save(new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));

            // act
            paymentService.assignTransactionKey(1L, "20250625:TR:abc123");

            // assert
            PaymentModel updated = paymentJpaRepository.findByOrderId(1L).orElseThrow();
            assertThat(updated.getTransactionKey()).isEqualTo("20250625:TR:abc123");
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("존재하지 않는 orderId로 저장하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void assignTransactionKey_throwsException_whenNotFound() {
            assertThatThrownBy(() -> paymentService.assignTransactionKey(999L, "20250625:TR:abc123"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제를 성공 처리할 때,")
    @Nested
    class Success {

        @DisplayName("PENDING 결제를 transactionKey로 찾아 성공 처리하면, SUCCESS 상태로 변경된다.")
        @Test
        void success_changesStatusToSuccess() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.assignTransactionKey("20250625:TR:abc123");
            paymentJpaRepository.save(payment);

            // act
            paymentService.success("20250625:TR:abc123");

            // assert
            PaymentModel updated = paymentJpaRepository.findByTransactionKey("20250625:TR:abc123").orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("존재하지 않는 transactionKey로 성공 처리하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void success_throwsException_whenNotFound() {
            assertThatThrownBy(() -> paymentService.success("not-exist"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("콜백으로 결제를 실패 처리할 때,")
    @Nested
    class FailByTransactionKey {

        @DisplayName("PENDING 결제를 transactionKey로 찾아 실패 처리하면, FAILED 상태로 변경되고 reason이 저장된다.")
        @Test
        void failByTransactionKey_changesStatusToFailed() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.assignTransactionKey("20250625:TR:abc123");
            paymentJpaRepository.save(payment);

            // act
            paymentService.failByTransactionKey("20250625:TR:abc123", "한도 초과");

            // assert
            PaymentModel updated = paymentJpaRepository.findByTransactionKey("20250625:TR:abc123").orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(updated.getTransactionKey()).isEqualTo("20250625:TR:abc123");
            assertThat(updated.getFailureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("존재하지 않는 transactionKey로 실패 처리하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failByTransactionKey_throwsException_whenNotFound() {
            assertThatThrownBy(() -> paymentService.failByTransactionKey("not-exist", "한도 초과"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("즉시 실패로 결제를 실패 처리할 때,")
    @Nested
    class FailByOrderId {

        @DisplayName("PENDING 결제를 orderId로 찾아 실패 처리하면, FAILED 상태로 변경되고 transactionKey는 null이다.")
        @Test
        void failByOrderId_changesStatusToFailed_withNullTransactionKey() {
            // arrange
            paymentJpaRepository.save(new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));

            // act
            paymentService.failByOrderId(1L, "PG 서버 오류");

            // assert
            PaymentModel updated = paymentJpaRepository.findByOrderId(1L).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(updated.getTransactionKey()).isNull();
            assertThat(updated.getFailureReason()).isEqualTo("PG 서버 오류");
        }

        @DisplayName("존재하지 않는 orderId로 실패 처리하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failByOrderId_throwsException_whenNotFound() {
            assertThatThrownBy(() -> paymentService.failByOrderId(999L, "PG 서버 오류"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제를 ID로 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 결제를 조회하면, 결제가 반환된다.")
        @Test
        void getById_returnsPayment() {
            // arrange
            PaymentModel saved = paymentJpaRepository.save(new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));

            // act
            PaymentModel result = paymentService.getById(saved.getId());

            // assert
            assertThat(result.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않는 ID로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void getById_throwsException_whenNotFound() {
            assertThatThrownBy(() -> paymentService.getById(999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
