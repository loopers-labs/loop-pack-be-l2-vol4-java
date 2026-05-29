package com.loopers.domain.payment;

import com.loopers.domain.order.vo.Money;
import com.loopers.domain.payment.enums.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentServiceUnitTest {

    private InMemoryPaymentRepository paymentRepository;
    private PaymentService sut;

    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 0L; // BaseEntity.id 기본값
    private static final Long NON_EXISTENT_ID = 999L;
    private static final Money DEFAULT_AMOUNT = new Money(10000L);

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        sut = new PaymentService(paymentRepository);
    }

    private void saveDefaultPayment() {
        paymentRepository.save(new PaymentModel(ORDER_ID, DEFAULT_AMOUNT));
    }

    @DisplayName("결제 단건 조회 시,")
    @Nested
    class Get {

        @DisplayName("결제가 존재하면, 결제 정보를 반환한다.")
        @Test
        void returnsPayment_whenPaymentExists() {
            saveDefaultPayment();

            PaymentModel result = sut.get(PAYMENT_ID);

            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getAmount()).isEqualTo(DEFAULT_AMOUNT);
        }

        @DisplayName("결제가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenPaymentDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.get(NON_EXISTENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제 승인 시,")
    @Nested
    class Approve {

        @DisplayName("대기 상태이면, 승인 상태로 변경된다.")
        @Test
        void approvesPayment_whenStatusIsPending() {
            saveDefaultPayment();

            PaymentModel result = sut.approve(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        }

        @DisplayName("대기 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotPending() {
            saveDefaultPayment();
            sut.approve(PAYMENT_ID);

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.approve(PAYMENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 실패 처리 시,")
    @Nested
    class Fail {

        @DisplayName("대기 상태이면, 실패 상태로 변경된다.")
        @Test
        void failsPayment_whenStatusIsPending() {
            saveDefaultPayment();

            PaymentModel result = sut.fail(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("대기 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotPending() {
            saveDefaultPayment();
            sut.fail(PAYMENT_ID);

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.fail(PAYMENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 만료 처리 시,")
    @Nested
    class Expire {

        @DisplayName("대기 상태이면, 만료 상태로 변경된다.")
        @Test
        void expiresPayment_whenStatusIsPending() {
            saveDefaultPayment();

            PaymentModel result = sut.expire(PAYMENT_ID);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }

        @DisplayName("대기 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotPending() {
            saveDefaultPayment();
            sut.expire(PAYMENT_ID);

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.expire(PAYMENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
