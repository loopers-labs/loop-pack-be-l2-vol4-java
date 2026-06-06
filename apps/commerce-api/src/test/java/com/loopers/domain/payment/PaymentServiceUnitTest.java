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

    @DisplayName("결제 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 주문이면, PENDING 상태의 결제가 생성된다.")
        @Test
        void createsPayment_whenOrderIsValid() {
            PaymentModel result = sut.create(ORDER_ID, DEFAULT_AMOUNT);

            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getAmount()).isEqualTo(DEFAULT_AMOUNT);
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("동일 주문에 PENDING 결제가 이미 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenPendingPaymentAlreadyExists() {
            saveDefaultPayment();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.create(ORDER_ID, DEFAULT_AMOUNT));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
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

}
