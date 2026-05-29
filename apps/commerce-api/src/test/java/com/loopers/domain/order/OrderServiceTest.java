package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
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

class OrderServiceTest {

    private static final Long USER_ID = 1L;

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final OrderService orderService = new OrderService(orderRepository, productRepository);

    private ProductModel product(String name, long price, int stock) {
        return new ProductModel(1L, name, "설명", price, stock);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("정상 주문이면, 재고를 차감하고 주문을 저장한다.")
        @Test
        void decreasesStockAndSaves_whenValid() {
            // arrange
            ProductModel product = product("에어맥스", 1000L, 10);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            OrderModel order = orderService.createOrder(USER_ID, List.of(new OrderLine(11L, 2)));

            // assert
            assertThat(product.getStock()).isEqualTo(8);
            verify(productRepository).save(product);
            verify(orderRepository).save(any(OrderModel.class));
            assertThat(order.getTotalAmount()).isEqualTo(2000L);
            assertThat(order.getItems().get(0).getProductId()).isEqualTo(11L);
        }

        @DisplayName("여러 상품 주문이면, 각 재고를 차감하고 스냅샷·총액을 보존한다.")
        @Test
        void handlesMultipleLines() {
            // arrange
            ProductModel p1 = product("상품1", 1000L, 10);
            ProductModel p2 = product("상품2", 500L, 10);
            when(productRepository.find(11L)).thenReturn(Optional.of(p1));
            when(productRepository.find(22L)).thenReturn(Optional.of(p2));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            OrderModel order = orderService.createOrder(USER_ID, List.of(new OrderLine(11L, 2), new OrderLine(22L, 3)));

            // assert
            assertThat(p1.getStock()).isEqualTo(8);
            assertThat(p2.getStock()).isEqualTo(7);
            assertThat(order.getTotalAmount()).isEqualTo(3500L);
            assertThat(order.getItems()).hasSize(2);
            OrderItemModel first = order.getItems().get(0);
            assertAll(
                () -> assertThat(first.getProductId()).isEqualTo(11L),
                () -> assertThat(first.getProductNameSnapshot()).isEqualTo("상품1"),
                () -> assertThat(first.getPriceSnapshot()).isEqualTo(1000L),
                () -> assertThat(first.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            // arrange
            when(productRepository.find(11L)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.createOrder(USER_ID, List.of(new OrderLine(11L, 1))));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsBadRequest_whenStockInsufficient() {
            // arrange
            ProductModel product = product("에어맥스", 1000L, 1);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.createOrder(USER_ID, List.of(new OrderLine(11L, 5))));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생하고 상품 조회도 하지 않는다.")
        @Test
        void throwsBadRequest_whenLinesEmpty() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.createOrder(USER_ID, List.of()));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(productRepository, never()).find(any());
        }

        @DisplayName("주문 수량이 음수면, BAD_REQUEST 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsBadRequest_whenQuantityNegative() {
            // arrange
            ProductModel product = product("에어맥스", 1000L, 10);
            when(productRepository.find(11L)).thenReturn(Optional.of(product));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.createOrder(USER_ID, List.of(new OrderLine(11L, -1))));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderRepository, never()).save(any());
        }
    }
}
