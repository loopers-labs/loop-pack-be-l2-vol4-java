package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @DisplayName("결제를 PENDING 으로 적재할 때, ")
    @Nested
    class CreatePending {

        @DisplayName("PENDING 결제를 저장하고 그대로 반환한다.")
        @Test
        void savesPendingPayment() {
            // given
            given(paymentRepository.save(any(PaymentModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentModel result = paymentService.createPending(1L, 100L, 50_000L);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getOrderId()).isEqualTo(100L);
            verify(paymentRepository).save(any(PaymentModel.class));
        }
    }

    @DisplayName("transactionKey 를 반영할 때, ")
    @Nested
    class AssignTransactionKey {

        @DisplayName("결제를 불러와 키를 부여하고 저장한다.")
        @Test
        void loadsAssignsAndSaves() {
            // given
            PaymentModel payment = PaymentModel.createPending(1L, 100L, 50_000L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(PaymentModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            paymentService.assignTransactionKey(10L, "20260624:TR:abc123");

            // then
            assertThat(payment.getTransactionKey()).isEqualTo("20260624:TR:abc123");
            verify(paymentRepository).save(payment);
        }

        @DisplayName("결제가 존재하지 않으면, PAYMENT_NOT_FOUND 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsPaymentNotFound_whenMissing() {
            // given
            given(paymentRepository.findById(10L)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> paymentService.assignTransactionKey(10L, "20260624:TR:abc123"));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND);
            verify(paymentRepository, never()).save(any(PaymentModel.class));
        }
    }
}
