package com.loopers.application.payment;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentConfirmation paymentConfirmation;

    @DisplayName("결제 결과를 확정할 때, ")
    @Nested
    class Confirm {

        @DisplayName("SUCCESS 면, 결제를 성공 처리하고 그 주문을 결제완료로 전이한다.")
        @Test
        void marksPaymentSuccessAndOrderPaid_whenSuccess() {
            // given
            given(paymentService.markSuccess("key", "정상 승인")).willReturn(paymentOfOrder(100L));

            // when
            paymentConfirmation.confirm("key", PaymentStatus.SUCCESS, "정상 승인");

            // then
            verify(paymentService).markSuccess("key", "정상 승인");
            verify(orderService).markPaid(100L);
        }

        @DisplayName("FAILED 면, 결제를 실패 처리하고 그 주문을 결제실패로 전이한다.")
        @Test
        void marksPaymentFailedAndOrderFailed_whenFailed() {
            // given
            given(paymentService.markFailed("key", "한도초과")).willReturn(paymentOfOrder(100L));

            // when
            paymentConfirmation.confirm("key", PaymentStatus.FAILED, "한도초과");

            // then
            verify(paymentService).markFailed("key", "한도초과");
            verify(orderService).markPaymentFailed(100L);
        }

        @DisplayName("PENDING(미확정) 이면, 아무 전이도 하지 않는다 (no-op).")
        @Test
        void doesNothing_whenPending() {
            // when
            paymentConfirmation.confirm("key", PaymentStatus.PENDING, null);

            // then
            verifyNoInteractions(paymentService, orderService);
        }
    }

    private PaymentModel paymentOfOrder(Long orderId) {
        return PaymentModel.createPending(1L, orderId, 50_000L);
    }
}
