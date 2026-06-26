package com.loopers.domain.payment;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentModel pending() {
        return new PaymentModel(1L, 10L, CardType.SAMSUNG, Money.of(5_000L));
    }

    @DisplayName("결제 접수(createPending) 시")
    @Nested
    class CreatePending {

        @DisplayName("동일 주문 결제가 없으면 PENDING 결제를 새로 저장한다")
        @Test
        void savesNew_whenNoneExists() {
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(PaymentModel.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentModel result = paymentService.createPending(1L, 10L, CardType.SAMSUNG, Money.of(5_000L));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository).save(any(PaymentModel.class));
        }

        @DisplayName("동일 주문에 PENDING 결제가 이미 있으면 저장하지 않고 기존 결제를 재사용한다 (멱등)")
        @Test
        void reusesExistingPending() {
            PaymentModel existing = pending();
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(existing));

            PaymentModel result = paymentService.createPending(1L, 10L, CardType.SAMSUNG, Money.of(5_000L));

            assertThat(result).isSameAs(existing);
            verify(paymentRepository, never()).save(any(PaymentModel.class));
        }

        @DisplayName("동일 주문에 이미 확정된(SUCCESS) 결제가 있으면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenAlreadyConfirmed() {
            PaymentModel confirmed = pending();
            confirmed.markSuccess();
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(confirmed));

            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(1L, 10L, CardType.SAMSUNG, Money.of(5_000L)));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("동시 최초 생성 경합으로 UNIQUE 충돌이 발생하면 CONFLICT로 변환한다")
        @Test
        void throwsConflict_whenUniqueViolation() {
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(PaymentModel.class)))
                .thenThrow(new DataIntegrityViolationException("uk_payment_order_id"));

            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(1L, 10L, CardType.SAMSUNG, Money.of(5_000L)));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결제 확정(confirm) 시")
    @Nested
    class Confirm {

        @DisplayName("success=true이면 결제가 SUCCESS로 확정되고 PaymentCompleted 이벤트가 발행된다")
        @Test
        void marksSuccess() {
            PaymentModel payment = pending();
            payment.assignTransactionKey("tx-1");
            when(paymentRepository.findByTransactionKey("tx-1")).thenReturn(Optional.of(payment));

            paymentService.confirm("tx-1", true, null);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(eventPublisher).publishEvent(any(PaymentCompleted.class));
        }

        @DisplayName("이미 확정된 결제를 다시 confirm하면 상태는 그대로이고 이벤트도 재발행되지 않는다 (멱등)")
        @Test
        void isIdempotent_whenAlreadyConfirmed() {
            PaymentModel payment = pending();
            payment.assignTransactionKey("tx-1");
            payment.markSuccess();
            when(paymentRepository.findByTransactionKey("tx-1")).thenReturn(Optional.of(payment));

            paymentService.confirm("tx-1", false, "한도 초과");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @DisplayName("success=false이면 결제가 FAILED로 확정되고 사유가 기록되며 PaymentFailed 이벤트가 발행된다")
        @Test
        void marksFailed() {
            PaymentModel payment = pending();
            payment.assignTransactionKey("tx-1");
            when(paymentRepository.findByTransactionKey("tx-1")).thenReturn(Optional.of(payment));

            paymentService.confirm("tx-1", false, "한도 초과");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("한도 초과");
            verify(eventPublisher).publishEvent(any(PaymentFailed.class));
        }

        @DisplayName("거래키에 해당하는 결제가 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenMissing() {
            when(paymentRepository.findByTransactionKey("none")).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.confirm("none", true, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 기준 조회(getByOrderId) 시")
    @Nested
    class GetByOrderId {

        @DisplayName("결제가 있으면 해당 결제를 그대로 반환한다")
        @Test
        void returnsPayment_whenExists() {
            PaymentModel payment = pending();
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

            PaymentModel result = paymentService.getByOrderId(1L);

            assertThat(result).isSameAs(payment);
        }

        @DisplayName("결제가 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenMissing() {
            when(paymentRepository.findByOrderId(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> paymentService.getByOrderId(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
