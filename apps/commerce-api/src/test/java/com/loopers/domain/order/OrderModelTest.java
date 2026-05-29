package com.loopers.domain.order;

import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    private OrderModel createOrder() {
        return new OrderModel(1L);
    }

    private OrderItemModel createItem(OrderModel order, Long stockId, int quantity) {
        return new OrderItemModel(order, stockId, 1L, new ProductName("테스트상품"), new Price(10000L), new StockQuantity(quantity));
    }

    @DisplayName("주문 아이템 수량 합산 시,")
    @Nested
    class Merge {

        @DisplayName("동일한 stockId가 여러 개면, 수량이 합산된다.")
        @Test
        void mergesQuantities_whenSameStockIdExists() {
            OrderModel order = createOrder();
            List<OrderItemInput> inputs = List.of(
                    new OrderItemInput(1L, 2),
                    new OrderItemInput(1L, 3),
                    new OrderItemInput(2L, 1)
            );

            List<OrderItemInput> merged = order.merge(inputs);

            assertThat(merged).hasSize(2);
            merged.stream()
                    .filter(i -> i.stockId().equals(1L))
                    .findFirst()
                    .ifPresent(i -> assertThat(i.quantity()).isEqualTo(5));
        }

        @DisplayName("중복 없는 입력이면, 그대로 반환된다.")
        @Test
        void returnsAsIs_whenNoDuplicates() {
            OrderModel order = createOrder();
            List<OrderItemInput> inputs = List.of(
                    new OrderItemInput(1L, 2),
                    new OrderItemInput(2L, 3)
            );

            List<OrderItemInput> merged = order.merge(inputs);

            assertThat(merged).hasSize(2);
        }
    }

    @DisplayName("주문 아이템 추가 시,")
    @Nested
    class AddItem {

        @DisplayName("아이템이 추가되면, 주문 아이템 목록에 포함된다.")
        @Test
        void addsItem_whenItemIsValid() {
            OrderModel order = createOrder();
            OrderItemModel item = createItem(order, 1L, 2);

            order.addItem(item);

            assertThat(order.getItems()).hasSize(1);
        }
    }

    @DisplayName("주문 완료 처리 시,")
    @Nested
    class Complete {

        @DisplayName("주문 요청 상태면, 완료 상태로 변경된다.")
        @Test
        void completesOrder_whenStatusIsRequested() {
            OrderModel order = createOrder();

            order.complete();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @DisplayName("주문 요청 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotRequested() {
            OrderModel order = createOrder();
            order.complete();

            CoreException exception = assertThrows(CoreException.class, order::complete);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 취소 처리 시,")
    @Nested
    class Cancel {

        @DisplayName("주문 요청 상태면, 취소 상태로 변경된다.")
        @Test
        void cancelsOrder_whenStatusIsRequested() {
            OrderModel order = createOrder();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("주문 요청 상태가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsNotRequested() {
            OrderModel order = createOrder();
            order.complete();

            CoreException exception = assertThrows(CoreException.class, order::cancel);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
