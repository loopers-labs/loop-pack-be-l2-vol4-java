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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentModel pending() {
        return PaymentModel.of(1L, 99L, 10000L, CardType.SAMSUNG, "1234-1234-1234-1234");
    }

    @DisplayName("PENDING 선삽입할 때")
    @Nested
    class CreatePending {

        @DisplayName("UNIQUE 위반(이미 접수된 주문)이면 예외 발생한다.")
        @Test
        void rejectsDuplicate() {
            given(paymentRepository.save(any())).willThrow(new DataIntegrityViolationException("duplicate"));

            CoreException result = assertThrows(CoreException.class,
                    () -> paymentService.createPending(1L, 99L, 10000L, CardType.SAMSUNG, "1234-1234-1234-1234"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결과를 반영할 때")
    @Nested
    class ApplyResult {

        @DisplayName("금액이 주문 금액과 다르면 예외 발생한다.")
        @Test
        void rejectsAmountMismatch() {
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(pending()));

            CoreException result = assertThrows(CoreException.class,
                    () -> paymentService.applyResult(1L, "tx", PaymentStatus.SUCCESS, 9999L, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("금액이 일치하면 SUCCESS 상태로 변경된다.")
        @Test
        void appliesSuccess() {
            PaymentModel payment = pending();
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.applyResult(1L, "tx", PaymentStatus.SUCCESS, 10000L, null);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("타임아웃으로 key 가 없던 결제는 콜백/폴링의 transactionKey 로 보정된다.")
        @Test
        void reconcilesMissingTransactionKey() {
            PaymentModel payment = pending();
            payment.markAttemptedWithoutKey();
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.applyResult(1L, "tx-late", PaymentStatus.SUCCESS, 10000L, null);

            assertThat(result.getTransactionKey()).isEqualTo("tx-late");
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("이미 완료된 결제에 다른 결과가 와도 무시한다(멱등).")
        @Test
        void ignoresWhenAlreadyFinal() {
            PaymentModel payment = pending();
            payment.succeed();
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.applyResult(1L, "tx", PaymentStatus.FAILED, 10000L, "뒤늦은 실패");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }
}