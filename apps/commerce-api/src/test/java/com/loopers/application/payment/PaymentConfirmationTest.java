package com.loopers.application.payment;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentGatewayTransaction;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    @DisplayName("결제 결과를 확정할 때(by transactionKey), ")
    @Nested
    class Confirm {

        @DisplayName("SUCCESS 면, 결제를 성공 처리하고 그 주문을 결제완료로 전이한다.")
        @Test
        void marksPaymentSuccessAndOrderPaid_whenSuccess() {
            // given
            PaymentModel payment = paymentOfOrder(100L);
            given(paymentService.getByTransactionKey("key")).willReturn(payment);

            // when
            paymentConfirmation.confirm("key", PaymentStatus.SUCCESS, "정상 승인");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(orderService).markPaid(100L);
            verify(paymentService).save(payment);
        }

        @DisplayName("FAILED 면, 결제를 실패 처리하고 그 주문을 결제실패로 전이한다.")
        @Test
        void marksPaymentFailedAndOrderFailed_whenFailed() {
            // given
            PaymentModel payment = paymentOfOrder(100L);
            given(paymentService.getByTransactionKey("key")).willReturn(payment);

            // when
            paymentConfirmation.confirm("key", PaymentStatus.FAILED, "한도초과");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(orderService).markPaymentFailed(100L);
            verify(paymentService).save(payment);
        }

        @DisplayName("PENDING(미확정) 이면, 결제를 조회조차 하지 않고 아무 전이도 하지 않는다 (no-op).")
        @Test
        void doesNothing_whenPending() {
            // when
            paymentConfirmation.confirm("key", PaymentStatus.PENDING, null);

            // then
            verifyNoInteractions(paymentService, orderService);
        }
    }

    @DisplayName("orderId 로 되물은 대표 결과를 수렴할 때(by order), ")
    @Nested
    class ConfirmByOrder {

        @DisplayName("결과가 key 없는 FAILED(PG 미접수)면, key 흡수 없이 결제를 실패로 정정하고 주문을 결제실패로 전이한다.")
        @Test
        void marksFailedWithoutKey_whenOutcomeFailedAndKeyless() {
            // given
            PaymentModel payment = paymentOfOrder(100L);
            given(paymentService.getById(5L)).willReturn(payment);
            PaymentGatewayTransaction outcome = new PaymentGatewayTransaction(null, PaymentStatus.FAILED, "PG 미접수");

            // when
            paymentConfirmation.confirmByOrder(5L, outcome);

            // then
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(orderService).markPaymentFailed(100L);
            verify(paymentService).save(payment);
        }

        @DisplayName("결과가 SUCCESS 면, transactionKey 를 흡수하고 결제를 성공 처리하며 주문을 결제완료로 전이한다.")
        @Test
        void absorbsKeyAndMarksSuccess_whenOutcomeSuccess() {
            // given
            PaymentModel payment = paymentOfOrder(100L);
            given(paymentService.getById(5L)).willReturn(payment);
            PaymentGatewayTransaction outcome = new PaymentGatewayTransaction("key-success", PaymentStatus.SUCCESS, "정상 승인");

            // when
            paymentConfirmation.confirmByOrder(5L, outcome);

            // then
            assertThat(payment.getTransactionKey()).isEqualTo("key-success");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(orderService).markPaid(100L);
            verify(paymentService).save(payment);
        }

        @DisplayName("결과가 아직 PENDING 이면, transactionKey 만 흡수하고 전이는 미루어 다음 스캔에서 keyed 로 수렴되게 한다.")
        @Test
        void absorbsKeyButStaysPending_whenOutcomePending() {
            // given
            PaymentModel payment = paymentOfOrder(100L);
            given(paymentService.getById(5L)).willReturn(payment);
            PaymentGatewayTransaction outcome = new PaymentGatewayTransaction("key-pending", PaymentStatus.PENDING, null);

            // when
            paymentConfirmation.confirmByOrder(5L, outcome);

            // then
            assertThat(payment.getTransactionKey()).isEqualTo("key-pending");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verifyNoInteractions(orderService);
            verify(paymentService).save(payment);
        }
    }

    private PaymentModel paymentOfOrder(Long orderId) {
        return PaymentModel.createPending(1L, orderId, 50_000L);
    }
}
