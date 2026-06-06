package com.loopers.domain.payment;

import com.loopers.domain.order.vo.Money;
import com.loopers.domain.payment.enums.PaymentStatus;
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

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long ORDER_ID = 1L;
    private static final Money DEFAULT_AMOUNT = new Money(10000L);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private PaymentModel savePayment() {
        return paymentRepository.save(new PaymentModel(ORDER_ID, DEFAULT_AMOUNT));
    }

    @DisplayName("결제 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 주문이면, PENDING 상태로 DB에 저장된다.")
        @Test
        void createsPayment_whenOrderIsValid() {
            PaymentModel result = paymentService.create(ORDER_ID, DEFAULT_AMOUNT);

            PaymentModel found = paymentRepository.findById(result.getId()).orElseThrow();
            assertThat(found.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(found.getAmount()).isEqualTo(DEFAULT_AMOUNT);
            assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("동일 주문에 PENDING 결제가 이미 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenPendingPaymentAlreadyExists() {
            savePayment();

            CoreException exception = assertThrows(CoreException.class,
                    () -> paymentService.create(ORDER_ID, DEFAULT_AMOUNT));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결제 승인 시,")
    @Nested
    class Approve {

        @DisplayName("PENDING 상태이면, APPROVED 상태로 변경되어 저장된다.")
        @Test
        void approvesPayment_whenStatusIsPending() {
            PaymentModel payment = savePayment();

            paymentService.approve(payment.getId());

            PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        }
    }

    @DisplayName("결제 실패 처리 시,")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태이면, FAILED 상태로 변경되어 저장된다.")
        @Test
        void failsPayment_whenStatusIsPending() {
            PaymentModel payment = savePayment();

            paymentService.fail(payment.getId());

            PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @DisplayName("결제 만료 처리 시,")
    @Nested
    class Expire {

        @DisplayName("PENDING 상태이면, EXPIRED 상태로 변경되어 저장된다.")
        @Test
        void expiresPayment_whenStatusIsPending() {
            PaymentModel payment = savePayment();

            paymentService.expire(payment.getId());

            PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }
    }
}
