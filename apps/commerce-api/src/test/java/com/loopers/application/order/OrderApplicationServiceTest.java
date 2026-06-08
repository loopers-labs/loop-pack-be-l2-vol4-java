package com.loopers.application.order;

import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("domain")
class OrderApplicationServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final OrderDomainService orderDomainService = new OrderDomainService();
    private final OrderApplicationService orderApplicationService =
        new OrderApplicationService(orderRepository, productRepository, orderDomainService);

    private static final Long USER_ID = 100L;

    private ProductModel product(Long id, String name, Long price, Integer stock) {
        return new ProductModel(id, 1L, name, "설명", price, stock, null, null);
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    class CreateOrder {

        @DisplayName("재고를 차감하고 상품/주문을 저장한 뒤 OrderInfo를 반환한다.")
        @Test
        void deductsStock_savesProductsAndOrder() {
            // arrange
            ProductModel productA = product(1L, "상품A", 1_000L, 10);
            when(productRepository.find(1L)).thenReturn(Optional.of(productA));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(1L, 2)));

            // act
            OrderInfo result = orderApplicationService.createOrder(USER_ID, command);

            // assert
            assertAll(
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.totalPrice()).isEqualTo(2_000L),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING.name()),
                () -> assertThat(productA.getStock()).isEqualTo(8)
            );
            verify(productRepository).save(productA);
            verify(orderRepository).save(any(OrderModel.class));
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생하고 주문이 저장되지 않는다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            when(productRepository.find(99L)).thenReturn(Optional.empty());
            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(99L, 1)));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderApplicationService.createOrder(USER_ID, command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생하고 주문이 저장되지 않는다.")
        @Test
        void throwsBadRequest_whenStockInsufficient() {
            // arrange
            when(productRepository.find(1L)).thenReturn(Optional.of(product(1L, "상품A", 1_000L, 1)));
            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(1L, 5)));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderApplicationService.createOrder(USER_ID, command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderApplicationService.createOrder(USER_ID, new OrderCommand(List.of())));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 단건 조회 시, ")
    @Nested
    class GetOrder {

        @DisplayName("다른 유저의 주문을 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            OrderModel order = new OrderModel(
                1L, 999L,
                List.of(com.loopers.domain.order.OrderLine.create(1L, "상품A", 1_000L, 1)),
                1_000L, OrderStatus.PENDING, null, null
            );
            when(orderRepository.find(1L)).thenReturn(Optional.of(order));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderApplicationService.getOrder(USER_ID, 1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("주문이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            when(orderRepository.find(1L)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderApplicationService.getOrder(USER_ID, 1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("본인 주문이면 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOwnedByUser() {
            // arrange
            OrderModel order = new OrderModel(
                1L, USER_ID,
                List.of(com.loopers.domain.order.OrderLine.create(1L, "상품A", 1_000L, 1)),
                1_000L, OrderStatus.PENDING, null, null
            );
            when(orderRepository.find(1L)).thenReturn(Optional.of(order));

            // act
            OrderInfo result = orderApplicationService.getOrder(USER_ID, 1L);

            // assert
            assertThat(result.orderId()).isEqualTo(1L);
            assertThat(result.userId()).isEqualTo(USER_ID);
        }
    }
}
