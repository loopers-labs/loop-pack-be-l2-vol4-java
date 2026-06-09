package com.loopers.order.domain;

import com.loopers.coupon.domain.CouponModel;
import com.loopers.coupon.domain.CouponType;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
    }

    @DisplayName("getOrThrow를 호출할 때,")
    @Nested
    class GetOrThrow {

        @DisplayName("order가 존재하면, 해당 order를 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(
                new OrderItemModel(1L, "에어맥스", 150000L, 2)
            ));

            // act
            OrderModel result = orderService.getOrThrow(Optional.of(order));

            // assert
            assertThat(result).isEqualTo(order);
        }

        @DisplayName("order가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.getOrThrow(Optional.empty())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("checkOwnership을 호출할 때,")
    @Nested
    class CheckOwnership {

        @DisplayName("주문 소유자가 맞으면, 예외 없이 통과한다.")
        @Test
        void doesNotThrow_whenUserIsOwner() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));

            // act & assert
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                orderService.checkOwnership(order, 1L)
            );
        }

        @DisplayName("주문 소유자가 아니면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenUserIsNotOwner() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.checkOwnership(order, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    @DisplayName("createOrder를 호출할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, PENDING_PAYMENT 상태의 OrderModel을 반환한다.")
        @Test
        void returnsOrderModelWithPendingPaymentStatus_whenRequestIsValid() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);
            // [fix] getId()가 null이 될 경우 Map.of에서 NPE 발생 가능 → 명시적 ID 설정으로 안전하게 수정
            ReflectionTestUtils.setField(product, "id", 1L);
            Map<Long, Integer> quantities = Map.of(product.getId(), 2);

            // act
            OrderModel result = orderService.createOrder(1L, List.of(product), quantities);

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo(1L),
                () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT),
                () -> assertThat(result.getItems()).hasSize(1),
                () -> assertThat(result.getItems().get(0).getProductName()).isEqualTo("에어맥스"),
                () -> assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("quantities 맵에 해당 productId가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdNotInQuantities() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);
            ReflectionTestUtils.setField(product, "id", 1L);
            Map<Long, Integer> quantities = Map.of(999L, 2);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.createOrder(1L, List.of(product), quantities)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰이 적용되면, originalAmount 기준으로 discountAmount를 계산한다.")
        @Test
        void appliesRateCouponDiscount_whenRateCouponProvided() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);
            ReflectionTestUtils.setField(product, "id", 1L);
            Map<Long, Integer> quantities = Map.of(1L, 2); // originalAmount = 300000
            CouponModel coupon = new CouponModel("10% 쿠폰", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(1));

            // act
            OrderModel result = orderService.createOrder(1L, List.of(product), quantities, coupon, 99L);

            // assert
            assertAll(
                () -> assertThat(result.getOriginalAmount()).isEqualTo(300000L),
                () -> assertThat(result.getDiscountAmount()).isEqualTo(30000L),
                () -> assertThat(result.getFinalAmount()).isEqualTo(270000L)
            );
        }

        @DisplayName("정액 쿠폰이 적용되면, 고정 금액만큼 discountAmount를 계산한다.")
        @Test
        void appliesFixedCouponDiscount_whenFixedCouponProvided() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);
            ReflectionTestUtils.setField(product, "id", 1L);
            Map<Long, Integer> quantities = Map.of(1L, 2); // originalAmount = 300000
            CouponModel coupon = new CouponModel("5000원 쿠폰", CouponType.FIXED, 5000L, null, ZonedDateTime.now().plusDays(1));

            // act
            OrderModel result = orderService.createOrder(1L, List.of(product), quantities, coupon, 99L);

            // assert
            assertAll(
                () -> assertThat(result.getOriginalAmount()).isEqualTo(300000L),
                () -> assertThat(result.getDiscountAmount()).isEqualTo(5000L),
                () -> assertThat(result.getFinalAmount()).isEqualTo(295000L)
            );
        }

        @DisplayName("최소 주문 금액 조건을 충족하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountNotMet() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);
            ReflectionTestUtils.setField(product, "id", 1L);
            Map<Long, Integer> quantities = Map.of(1L, 2); // originalAmount = 300000
            CouponModel coupon = new CouponModel("500000원 이상 쿠폰", CouponType.FIXED, 5000L, 500000L, ZonedDateTime.now().plusDays(1));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.createOrder(1L, List.of(product), quantities, coupon, 99L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
