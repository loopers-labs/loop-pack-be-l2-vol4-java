package com.loopers.application.payment;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLines;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentInitiatorTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentInitiator paymentInitiator;

    @DisplayName("결제를 개시(TX1)할 때, ")
    @Nested
    class Initiate {

        @DisplayName("주문을 결제대기로 전이하고, 그 주문의 finalAmount 로 결제를 PENDING 적재한다.")
        @Test
        void startsOrderAndCreatesPendingWithFinalAmount() {
            // given
            Long userId = 1L;
            Long orderId = 100L;
            OrderModel order = orderWithFinalAmount(userId, 50_000L);
            given(orderService.startPayment(userId, orderId)).willReturn(order);
            given(paymentService.createPending(userId, orderId, 50_000L))
                .willReturn(pendingPayment(userId, orderId, 50_000L, 10L));

            // when
            PaymentInitiator.Initiated initiated = paymentInitiator.initiate(userId, orderId);

            // then
            verify(orderService).startPayment(userId, orderId);
            verify(paymentService).createPending(userId, orderId, 50_000L);
            assertAll(
                () -> assertThat(initiated.paymentId()).isEqualTo(10L),
                () -> assertThat(initiated.amount()).isEqualTo(50_000L)
            );
        }
    }

    private OrderModel orderWithFinalAmount(Long userId, long finalAmount) {
        return OrderModel.create(userId, OrderLines.of(List.of(
            new OrderLine(100L, 1, "상품", finalAmount, "브랜드")
        )));
    }

    private PaymentModel pendingPayment(Long userId, Long orderId, Long amount, Long id) {
        PaymentModel payment = PaymentModel.createPending(userId, orderId, amount);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
