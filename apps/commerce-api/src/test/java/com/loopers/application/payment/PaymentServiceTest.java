package com.loopers.application.payment;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 1001L;

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final PaymentService paymentService =
        new PaymentService(paymentRepository, orderRepository, productRepository, userCouponRepository);

    private Order payableOrder(long finalAmount) {
        Order order = mock(Order.class);
        when(order.isOwnedBy(USER_ID)).thenReturn(true);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getFinalAmount()).thenReturn(finalAmount);
        return order;
    }

    private Payment pendingPaymentWithKey(String key) {
        Payment payment = Payment.pending(USER_ID, ORDER_ID, Money.of(5000L), CardType.SAMSUNG);
        payment.assignTransactionKey(key);
        return payment;
    }

    @DisplayName("결제를 접수(createPending)할 때, ")
    @Nested
    class CreatePending {

        @DisplayName("결제 가능한 주문이고 활성 결제가 없으면, 주문 금액으로 PENDING 결제를 만들어 먼저 저장한다.")
        @Test
        void createsAndSavesPending() {
            // arrange
            Order order = payableOrder(5000L);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findActiveByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            Payment payment = paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG);

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(payment.getAmount()).isEqualTo(5000L)
            );
            verify(paymentRepository).save(any(Payment.class)); // record-first
        }

        @DisplayName("이미 활성 결제가 있으면, 새로 만들지 않고 기존 결제를 반환한다. (멱등)")
        @Test
        void returnsExisting_whenActivePaymentExists() {
            // arrange
            Payment existing = Payment.pending(USER_ID, ORDER_ID, Money.of(5000L), CardType.SAMSUNG);
            Order order = payableOrder(5000L);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findActiveByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            // act
            Payment payment = paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG);

            // assert
            assertThat(payment).isSameAs(existing);
            verify(paymentRepository, never()).save(any());
        }

        @DisplayName("주문이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderMissing() {
            // arrange
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.empty());

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            // arrange
            Order order = mock(Order.class);
            when(order.isOwnedBy(USER_ID)).thenReturn(false);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 결제 완료(PAID)된 주문이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderNotPayable() {
            // arrange
            Order order = mock(Order.class);
            when(order.isOwnedBy(USER_ID)).thenReturn(true);
            when(order.getStatus()).thenReturn(OrderStatus.PAID);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("PG 거래키를 반영(attachTransactionKey)할 때, ")
    @Nested
    class AttachTransactionKey {

        @DisplayName("PENDING 결제에 거래키를 기록하고 저장한다.")
        @Test
        void attachesKey() {
            // arrange
            Payment payment = Payment.pending(USER_ID, ORDER_ID, Money.of(5000L), CardType.SAMSUNG);
            when(paymentRepository.find(99L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            Payment result = paymentService.attachTransactionKey(99L, "20260625:TR:abc123");

            // assert
            assertThat(result.getTransactionKey()).isEqualTo("20260625:TR:abc123");
            verify(paymentRepository).save(payment);
        }

        @DisplayName("결제가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenPaymentMissing() {
            // arrange
            when(paymentRepository.find(99L)).thenReturn(Optional.empty());

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.attachTransactionKey(99L, "k"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("PG 결과를 반영(applyResult)할 때, ")
    @Nested
    class ApplyResult {

        @DisplayName("SUCCESS 면, 결제 SUCCESS·주문 PAID 로 전이하고 저장한다.")
        @Test
        void onSuccess() {
            // arrange
            Payment payment = pendingPaymentWithKey("TKEY");
            Order order = new Order(USER_ID, List.of(new OrderItem(10L, "상품", Money.of(5000L), Quantity.of(1))));
            when(paymentRepository.findByTransactionKey("TKEY")).thenReturn(Optional.of(payment));
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));

            // act
            paymentService.applyResult("TKEY", PaymentStatus.SUCCESS, null);

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
            verify(paymentRepository).save(payment);
            verify(orderRepository).save(order);
        }

        @DisplayName("FAILED 면, 결제·주문 FAILED 로 전이하고 재고·쿠폰을 복원한다. (보상)")
        @Test
        void onFailed_compensates() {
            // arrange
            Payment payment = pendingPaymentWithKey("TKEY");
            Order order = new Order(USER_ID,
                List.of(new OrderItem(10L, "상품", Money.of(5000L), Quantity.of(2))), Money.of(500L), 99L);
            Product product = mock(Product.class);
            when(product.getId()).thenReturn(10L);
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = coupon.issueTo(USER_ID, ZonedDateTime.now());
            userCoupon.use(USER_ID, ZonedDateTime.now());
            when(paymentRepository.findByTransactionKey("TKEY")).thenReturn(Optional.of(payment));
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));
            when(productRepository.findAllForUpdate(List.of(10L))).thenReturn(List.of(product));
            when(userCouponRepository.find(99L)).thenReturn(Optional.of(userCoupon));

            // act
            paymentService.applyResult("TKEY", PaymentStatus.FAILED, "한도 초과");

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도 초과"),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
            verify(product).increaseStock(argThat(q -> q.getValue() == 2)); // 재고 2 복원
        }

        @DisplayName("이미 터미널인 결제면(중복 도착), 아무 것도 하지 않는다. (멱등 — 흡수상태)")
        @Test
        void duplicate_isNoOp() {
            // arrange
            Payment payment = pendingPaymentWithKey("TKEY");
            payment.markSuccess(); // 이미 터미널
            when(paymentRepository.findByTransactionKey("TKEY")).thenReturn(Optional.of(payment));

            // act
            paymentService.applyResult("TKEY", PaymentStatus.SUCCESS, null);

            // assert
            verify(orderRepository, never()).find(anyLong());
            verify(paymentRepository, never()).save(any());
        }

        @DisplayName("거래키로 결제를 못 찾으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenPaymentMissing() {
            // arrange
            when(paymentRepository.findByTransactionKey("X")).thenReturn(Optional.empty());

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.applyResult("X", PaymentStatus.SUCCESS, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
