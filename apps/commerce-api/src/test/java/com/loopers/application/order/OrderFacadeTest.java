package com.loopers.application.order;

import com.loopers.domain.coupon.CouponUseResult;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderProductProcessService;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @Mock
    private OrderProductProcessService orderProductProcessService;

    @Mock
    private CouponService couponService;

    private OrderFacade orderFacade;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
            Instant.parse("2026-06-12T01:30:00Z"),
            ZoneId.of("Asia/Seoul")
        );
        orderFacade = new OrderFacade(
            orderService,
            productService,
            orderProductProcessService,
            couponService,
            clock
        );
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("주문 상품 목록이 null이면 락 조회 전에 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestBeforeLock_whenCommandsAreNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderFacade.createOrder("user1234", null, null);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> verifyNoInteractions(productService, orderProductProcessService, couponService, orderService)
            );
        }

        @DisplayName("주문 상품 목록이 비어 있으면 락 조회 전에 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestBeforeLock_whenCommandsAreEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderFacade.createOrder("user1234", List.of(), null);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> verifyNoInteractions(productService, orderProductProcessService, couponService, orderService)
            );
        }

        @DisplayName("주문 상품 목록에 null 항목이 있으면 락 조회 전에 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestBeforeLock_whenCommandContainsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderFacade.createOrder("user1234", java.util.Arrays.asList(
                    new OrderProductCommand(1L, 1),
                    null
                ), null);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> verifyNoInteractions(productService, orderProductProcessService, couponService, orderService)
            );
        }

        @DisplayName("쿠폰을 적용하면 주입된 Clock 기준 시각으로 쿠폰을 사용한다.")
        @Test
        void usesCouponWithInjectedClock_whenCouponIsApplied() {
            // arrange
            Long couponId = 10L;
            List<OrderProductCommand> commands = List.of(new OrderProductCommand(1L, 1));
            Product product = Product.reconstruct(1L, 1L, "니트", "부드러운 니트", 10_000L, 10, 0, false);
            Order order = new Order("user1234", List.of(new OrderLine(1L, "니트", 10_000L, 1)));
            OrderResult orderResult = new OrderResult(order, List.of());
            ZonedDateTime fixedNow = ZonedDateTime.now(clock);

            when(productService.findProductsByIdsForUpdate(List.of(1L))).thenReturn(List.of(product));
            when(orderProductProcessService.createOrder("user1234", commands, List.of(product))).thenReturn(orderResult);
            when(couponService.useCoupon("user1234", couponId, 10_000L, fixedNow))
                .thenReturn(new CouponUseResult(1L, couponId, 1_000L));
            when(orderService.saveOrder(orderResult)).thenReturn(orderResult);

            // act
            OrderInfo result = orderFacade.createOrder("user1234", commands, couponId);

            // assert
            assertAll(
                () -> assertThat(result.discountAmount()).isEqualTo(1_000L),
                () -> verify(couponService).useCoupon(
                    eq("user1234"),
                    eq(couponId),
                    eq(10_000L),
                    eq(fixedNow)
                )
            );
        }
    }
}
