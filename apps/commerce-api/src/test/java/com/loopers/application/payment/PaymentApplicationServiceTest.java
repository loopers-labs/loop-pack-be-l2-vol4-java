package com.loopers.application.payment;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("domain")
class PaymentApplicationServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final PaymentApplicationService service = new PaymentApplicationService(paymentRepository, orderRepository, productRepository);

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;

    private static OrderModel order(Long userId, OrderStatus status) {
        List<OrderLine> lines = List.of(OrderLine.create(10L, "상품", 1000L, 5));
        return new OrderModel(ORDER_ID, userId, lines, 5000L, 0L, 5000L, status, null, null);
    }

    private static PaymentCommand command() {
        return new PaymentCommand(ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451");
    }

    @Nested
    @DisplayName("결제 등록 시")
    class Register {

        @Test
        @DisplayName("기존 결제가 없으면 PENDING 결제를 새로 저장한다")
        void savesNewPaymentWithOrderAmount() {
            // arrange
            when(orderRepository.find(ORDER_ID)).thenReturn(java.util.Optional.of(order(USER_ID, OrderStatus.PENDING)));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // act
            PaymentModel result = service.register(USER_ID, command());

            // assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getAmount()).isEqualTo(5000L);
            verify(paymentRepository).save(any());
        }

        @Test
        @DisplayName("진행중/성공 결제가 이미 있으면 새로 저장하지 않고 재사용한다")
        void reusesExistingPayment() {
            // arrange
            PaymentModel existing = PaymentModel.create(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
            existing.attachTransactionKey("TR:exist");
            when(orderRepository.find(ORDER_ID)).thenReturn(java.util.Optional.of(order(USER_ID, OrderStatus.PENDING)));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(existing));

            // act
            PaymentModel result = service.register(USER_ID, command());

            // assert
            assertThat(result.getTransactionKey()).isEqualTo("TR:exist");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("주문이 없으면 NOT_FOUND")
        void orderNotFound() {
            when(orderRepository.find(ORDER_ID)).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> service.register(USER_ID, command()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("주문 소유자가 아니면 FORBIDDEN")
        void notOwner() {
            when(orderRepository.find(ORDER_ID)).thenReturn(java.util.Optional.of(order(999L, OrderStatus.PENDING)));

            assertThatThrownBy(() -> service.register(USER_ID, command()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.FORBIDDEN);
        }

        @Test
        @DisplayName("결제 가능한 상태(PENDING)가 아니면 BAD_REQUEST")
        void orderNotPayable() {
            when(orderRepository.find(ORDER_ID)).thenReturn(java.util.Optional.of(order(USER_ID, OrderStatus.PAID)));

            assertThatThrownBy(() -> service.register(USER_ID, command()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("거래키 반영 시")
    class AttachTransactionKey {

        @Test
        @DisplayName("결제건에 거래키를 반영한다")
        void attaches() {
            PaymentModel payment = new PaymentModel(7L, USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
                null, PaymentStatus.PENDING, null, null, null);
            when(paymentRepository.find(7L)).thenReturn(java.util.Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PaymentModel result = service.attachTransactionKey(7L, "TR:new");

            assertThat(result.getTransactionKey()).isEqualTo("TR:new");
        }
    }

    @Nested
    @DisplayName("결과 반영(confirm) 시")
    class Confirm {

        private PaymentModel pendingPaymentWithKey() {
            return new PaymentModel(7L, USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
                "TR:abc", PaymentStatus.PENDING, null, null, null);
        }

        @Test
        @DisplayName("SUCCESS 면 결제 성공 + 주문 PAID")
        void success() {
            // arrange
            OrderModel order = order(USER_ID, OrderStatus.PENDING);
            when(paymentRepository.findByTransactionKey("TR:abc")).thenReturn(Optional.of(pendingPaymentWithKey()));
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // act
            PaymentModel result = service.confirm("TR:abc", PgTransactionStatus.SUCCESS, null);

            // assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("FAILED 면 결제 실패 + 주문 CANCELLED + 재고 복원")
        void failedRestoresStock() {
            // arrange
            OrderModel order = order(USER_ID, OrderStatus.PENDING); // line: productId=10, qty=5
            ProductModel product = new ProductModel(10L, 1L, "상품", "설명", 1000L, 3, 0L, null, null);
            when(paymentRepository.findByTransactionKey("TR:abc")).thenReturn(Optional.of(pendingPaymentWithKey()));
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));
            when(productRepository.findWithLock(10L)).thenReturn(Optional.of(product));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // act
            PaymentModel result = service.confirm("TR:abc", PgTransactionStatus.FAILED, "한도 초과");

            // assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getReason()).isEqualTo("한도 초과");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(product.getStock()).isEqualTo(8); // 3 + 5 복원
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("이미 확정된 결제는 멱등하게 무시한다(주문 변경 없음)")
        void idempotentWhenAlreadyResolved() {
            // arrange
            PaymentModel alreadySuccess = new PaymentModel(7L, USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
                "TR:abc", PaymentStatus.SUCCESS, null, null, null);
            when(paymentRepository.findByTransactionKey("TR:abc")).thenReturn(Optional.of(alreadySuccess));

            // act
            service.confirm("TR:abc", PgTransactionStatus.FAILED, "한도 초과");

            // assert
            verify(orderRepository, never()).find(any());
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("거래키로 결제건을 찾지 못하면 NOT_FOUND")
        void notFound() {
            when(paymentRepository.findByTransactionKey("TR:none")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm("TR:none", PgTransactionStatus.SUCCESS, null))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
