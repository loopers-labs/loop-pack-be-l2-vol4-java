package com.loopers.application.order;

import com.loopers.domain.coupon.CouponSnapshot;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderFacadeTest {

    private static final String LOGIN_ID = "tester01";

    private final OrderService orderService = new OrderService(); // 순수 도메인 서비스(실물 사용)
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final OrderFacade orderFacade =
        new OrderFacade(orderService, orderRepository, productRepository, userRepository, userCouponRepository);

    private void givenUser(long id) {
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(id);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
    }

    private PlaceOrderCommand command(Long productId, int quantity) {
        return new PlaceOrderCommand(List.of(new PlaceOrderCommand.Item(productId, quantity)), null);
    }

    private PlaceOrderCommand commandWithCoupon(Long productId, int quantity, Long couponId) {
        return new PlaceOrderCommand(List.of(new PlaceOrderCommand.Item(productId, quantity)), couponId);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("유저 식별·상품 로드 후 재고를 차감하고 주문을 저장한다.")
        @Test
        void createsOrder() {
            // arrange
            ProductModel product = new ProductModel(1L, "에어맥스", "운동화", 1000L, 10);
            givenUser(7L);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            OrderInfo info = orderFacade.createOrder(LOGIN_ID, command(11L, 2));

            // assert
            assertAll(
                () -> assertThat(product.getStock()).isEqualTo(8),
                () -> assertThat(info.userId()).isEqualTo(7L),
                () -> assertThat(info.totalAmount()).isEqualTo(2000L)
            );
            verify(productRepository).save(product);
            verify(orderRepository).save(any(OrderModel.class));
        }

        @DisplayName("유저가 없으면 NOT_FOUND 이고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_whenUserMissing() {
            // arrange
            when(userRepository.findByLoginId("ghost")).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderFacade.createOrder("ghost", command(11L, 1)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("상품이 없으면 NOT_FOUND 이고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            // arrange
            givenUser(7L);
            when(productRepository.find(11L)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(LOGIN_ID, command(11L, 1)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 이고 주문은 저장되지 않는다.")
        @Test
        void throwsBadRequest_whenStockInsufficient() {
            // arrange
            ProductModel product = new ProductModel(1L, "에어맥스", "운동화", 1000L, 1);
            givenUser(7L);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(LOGIN_ID, command(11L, 5)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderRepository, never()).save(any());
        }
    }

    @DisplayName("쿠폰과 함께 주문을 생성할 때, ")
    @Nested
    class CreateOrderWithCoupon {

        private UserCoupon couponOwnedBy(long ownerId) {
            CouponSnapshot snapshot = new CouponSnapshot("5천원 할인", CouponType.FIXED, 5000L, null);
            return new UserCoupon(ownerId, 10L,
                snapshot, java.time.ZonedDateTime.now(), java.time.ZonedDateTime.now().plusDays(30));
        }

        @DisplayName("쿠폰을 사용 처리하고 할인 반영 후 쿠폰을 저장한다.")
        @Test
        void appliesCouponAndSaves() {
            // arrange
            ProductModel product = new ProductModel(1L, "에어맥스", "운동화", 10000L, 10);
            UserCoupon userCoupon = couponOwnedBy(7L);
            givenUser(7L);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));
            when(userCouponRepository.find(42L)).thenReturn(Optional.of(userCoupon));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            OrderInfo info = orderFacade.createOrder(LOGIN_ID, commandWithCoupon(11L, 2, 42L));

            // assert
            assertAll(
                () -> assertThat(info.totalAmount()).isEqualTo(20000L),
                () -> assertThat(info.discountAmount()).isEqualTo(5000L),
                () -> assertThat(info.finalAmount()).isEqualTo(15000L),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED)
            );
            verify(userCouponRepository).save(userCoupon);
        }

        @DisplayName("쿠폰이 존재하지 않으면 NOT_FOUND 이고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_whenCouponMissing() {
            // arrange
            ProductModel product = new ProductModel(1L, "에어맥스", "운동화", 10000L, 10);
            givenUser(7L);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));
            when(userCouponRepository.find(42L)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(LOGIN_ID, commandWithCoupon(11L, 1, 42L)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("쿠폰 없이 주문하면 쿠폰 저장은 호출되지 않는다.")
        @Test
        void skipsCouponSave_whenNoCoupon() {
            // arrange
            ProductModel product = new ProductModel(1L, "에어맥스", "운동화", 1000L, 10);
            givenUser(7L);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            orderFacade.createOrder(LOGIN_ID, command(11L, 1));

            // assert
            verify(userCouponRepository, never()).save(any());
        }
    }

    @DisplayName("주문을 조회할 때, ")
    @Nested
    class GetOrder {

        private OrderModel orderOwnedBy(long ownerId) {
            return new OrderModel(ownerId,
                List.of(new OrderItemModel(11L, "에어맥스", Money.of(1000L), Quantity.of(2))));
        }

        @DisplayName("본인 주문이면 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            // arrange
            givenUser(7L);
            when(orderRepository.find(100L)).thenReturn(Optional.of(orderOwnedBy(7L)));

            // act
            OrderInfo info = orderFacade.getOrder(LOGIN_ID, 100L);

            // assert
            assertThat(info.totalAmount()).isEqualTo(2000L);
        }

        @DisplayName("타 유저의 주문이면 NOT_FOUND 를 반환한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            // arrange
            givenUser(7L);
            when(orderRepository.find(100L)).thenReturn(Optional.of(orderOwnedBy(999L)));

            // act
            CoreException ex = assertThrows(CoreException.class, () -> orderFacade.getOrder(LOGIN_ID, 100L));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
