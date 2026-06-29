package com.loopers.order.application;

import com.loopers.coupon.domain.CouponIssueModel;
import com.loopers.coupon.domain.CouponModel;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.infrastructure.CouponIssueJpaRepository;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.order.domain.OrderStatus;
import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.infrastructure.StockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private CouponIssueJpaRepository couponIssueJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel savedProduct(int totalStock) {
        ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
        stockJpaRepository.save(new StockModel(product.getId(), totalStock));
        return product;
    }

    private CouponModel savedCoupon(CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponJpaRepository.save(new CouponModel("테스트 쿠폰", type, value, minOrderAmount, expiredAt));
    }

    private CouponIssueModel savedCouponIssue(Long couponId, Long userId) {
        return couponIssueJpaRepository.save(new CouponIssueModel(couponId, userId));
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, PENDING_PAYMENT 상태로 저장되고 재고가 즉시 선점된다.")
        @Test
        void returnsOrderInfo_andReservesStock_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);

            // act
            OrderInfo result = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 2)));

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name()),
                () -> assertThat(result.items()).hasSize(1)
            );

            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stock.getReservedStock()).isEqualTo(2),
                () -> assertThat(stock.availableStock()).isEqualTo(98)
            );
        }

        @DisplayName("존재하지 않는 productId가 포함되면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(999L, 1)))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        // [fix] 재고 검증이 createOrder로 이동됨에 따라 테스트 위치 변경
        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = savedProduct(1);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 5)))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        // [fix] 재고 검증이 createOrder로 이동됨에 따라 테스트 위치 변경
        @DisplayName("재고 레코드가 없는 상품이 포함되면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenStockNotExists() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰을 적용하여 주문을 생성할 때,")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("유효한 FIXED 쿠폰을 적용하면, 할인 금액이 정확히 계산된다.")
        @Test
        void calculatesAmountsCorrectly_whenFixedCouponIsApplied() {
            // arrange
            ProductModel product = savedProduct(100);  // 150,000원 × 2개 = 300,000원
            CouponModel coupon = savedCoupon(CouponType.FIXED, 10000L, null, ZonedDateTime.now().plusDays(30));
            savedCouponIssue(coupon.getId(), 1L);

            // act
            OrderInfo result = orderFacade.createOrder(
                1L, "user1", List.of(new OrderItemCommand(product.getId(), 2)), coupon.getId()
            );

            // assert
            assertAll(
                () -> assertThat(result.originalAmount()).isEqualTo(300000L),
                () -> assertThat(result.discountAmount()).isEqualTo(10000L),
                () -> assertThat(result.finalAmount()).isEqualTo(290000L)
            );
        }

        @DisplayName("유효한 RATE 쿠폰을 적용하면, 정률 할인 금액이 정확히 계산된다.")
        @Test
        void calculatesAmountsCorrectly_whenRateCouponIsApplied() {
            // arrange
            ProductModel product = savedProduct(100);  // 150,000원 × 1개 = 150,000원
            CouponModel coupon = savedCoupon(CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(30));
            savedCouponIssue(coupon.getId(), 1L);

            // act
            OrderInfo result = orderFacade.createOrder(
                1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)), coupon.getId()
            );

            // assert
            assertAll(
                () -> assertThat(result.originalAmount()).isEqualTo(150000L),
                () -> assertThat(result.discountAmount()).isEqualTo(15000L),
                () -> assertThat(result.finalAmount()).isEqualTo(135000L)
            );
        }

        @DisplayName("만료된 쿠폰을 적용하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // arrange
            ProductModel product = savedProduct(100);
            CouponModel coupon = savedCoupon(CouponType.FIXED, 1000L, null, ZonedDateTime.now().minusDays(1));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)), coupon.getId())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("보유하지 않은 쿠폰을 적용하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponIsNotOwned() {
            // arrange
            ProductModel product = savedProduct(100);
            CouponModel coupon = savedCoupon(CouponType.FIXED, 1000L, null, ZonedDateTime.now().plusDays(30));
            // 유저 2에게만 발급, 유저 1은 미보유

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)), coupon.getId())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰을 적용하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsAlreadyUsed() {
            // arrange
            ProductModel product = savedProduct(100);
            CouponModel coupon = savedCoupon(CouponType.FIXED, 1000L, null, ZonedDateTime.now().plusDays(30));
            CouponIssueModel issue = savedCouponIssue(coupon.getId(), 1L);
            couponIssueJpaRepository.updateStatusIfAvailable(issue.getId(), CouponStatus.USED, CouponStatus.AVAILABLE);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)), coupon.getId())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount 조건을 충족하지 못하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountIsBelowMinOrderAmount() {
            // arrange
            ProductModel product = savedProduct(100);  // 150,000원 × 1개 = 150,000원
            CouponModel coupon = savedCoupon(CouponType.FIXED, 1000L, 200000L, ZonedDateTime.now().plusDays(30));
            savedCouponIssue(coupon.getId(), 1L);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)), coupon.getId())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 생성이 완료되면, 쿠폰 상태가 즉시 USED로 변경된다.")
        @Test
        void changesCouponStatusToUsed_whenCreateOrderSucceeds() {
            // arrange
            ProductModel product = savedProduct(100);
            CouponModel coupon = savedCoupon(CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(30));
            CouponIssueModel issue = savedCouponIssue(coupon.getId(), 1L);

            // act
            orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)), coupon.getId());

            // assert
            CouponIssueModel updatedIssue = couponIssueJpaRepository.findById(issue.getId()).orElseThrow();
            assertThat(updatedIssue.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("쿠폰이 미보유 상태로 주문 생성이 실패하면, 재고 선점이 롤백된다.")
        @Test
        void rollsBackStockReservation_whenCouponIsNotOwned() {
            // arrange
            ProductModel product = savedProduct(100);
            CouponModel coupon = savedCoupon(CouponType.FIXED, 1000L, null, ZonedDateTime.now().plusDays(30));
            // 쿠폰 미발급: userId=1은 해당 쿠폰 미보유

            // act
            assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 5)), coupon.getId())
            );

            // assert
            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(stock.getReservedStock()).isEqualTo(0);
        }
    }

    @DisplayName("결제를 시작(startPayment)할 때,")
    @Nested
    class StartPayment {

        @DisplayName("정상 요청이면, createOrder에서 선점된 재고에 변동이 없다.")
        @Test
        void doesNotChangeStockReservation_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 5)));

            StockModel stockAfterCreate = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(stockAfterCreate.getReservedStock()).isEqualTo(5);

            // act
            orderFacade.startPayment(1L, order.id());

            // assert
            StockModel stockAfterStart = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stockAfterStart.getReservedStock()).isEqualTo(5),
                () -> assertThat(stockAfterStart.availableStock()).isEqualTo(95)
            );
        }

        @DisplayName("존재하지 않는 orderId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.startPayment(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("정상 요청이면, 주문 상태가 IN_PAYMENT로 변경된다.")
        @Test
        void changesStatusToInPayment_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            OrderInfo result = orderFacade.startPayment(1L, order.id());

            // assert
            assertThat(result.status()).isEqualTo(OrderStatus.IN_PAYMENT.name());
        }

        @DisplayName("이미 IN_PAYMENT 상태인 주문이면, BAD_REQUEST 예외가 발생하고 재고가 중복 선점되지 않는다.")
        @Test
        void throwsBadRequest_andDoesNotReserveStockAgain_whenStartPaymentIsCalledTwice() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 5)));
            orderFacade.startPayment(1L, order.id());

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.startPayment(1L, order.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(stock.getReservedStock()).isEqualTo(5);
        }

        @DisplayName("다른 유저의 주문이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.startPayment(2L, order.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

    }

    @DisplayName("결제를 확정(confirmPayment)할 때,")
    @Nested
    class ConfirmPayment {

        @DisplayName("정상 요청이면, 재고가 확정 차감되고 주문 상태가 CONFIRMED로 변경된다.")
        @Test
        void confirmsStockAndChangesStatusToConfirmed_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 3)));
            orderFacade.startPayment(1L, order.id());

            // act
            OrderInfo result = orderFacade.confirmPayment(1L, order.id());

            // assert
            assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED.name());

            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stock.getTotalStock()).isEqualTo(97),
                () -> assertThat(stock.getReservedStock()).isEqualTo(0)
            );
        }

        @DisplayName("존재하지 않는 orderId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.confirmPayment(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)));
            orderFacade.startPayment(1L, order.id());

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.confirmPayment(2L, order.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID이면, OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOrderExists() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo created = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            OrderInfo result = orderFacade.getOrder(1L, created.id());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.userId()).isEqualTo(1L),
                () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.getOrder(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문 ID이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo created = orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.getOrder(2L, created.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
