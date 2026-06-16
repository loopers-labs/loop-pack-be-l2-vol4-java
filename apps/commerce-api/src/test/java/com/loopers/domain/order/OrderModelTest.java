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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    private OrderModel createOrder() {
        return new OrderModel("20260101000000TEST01", 1L, null);
    }

    @DisplayName("주문 생성 시,")
    @Nested
    class Create {

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> new OrderModel("20260101000000TEST01", null, null));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    private OrderItemModel createItem(OrderModel order, Long stockId, int quantity) {
        return new OrderItemModel(order, stockId, 1L, new ProductName("테스트상품"), new Price(10000L), new StockQuantity(quantity));
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

    @DisplayName("결제 가능 여부 확인 시,")
    @Nested
    class IsPayable {

        @DisplayName("주문 요청 상태면, true를 반환한다.")
        @Test
        void returnsTrue_whenStatusIsRequested() {
            OrderModel order = createOrder();

            assertThat(order.isPayable()).isTrue();
        }

        @DisplayName("완료 상태면, false를 반환한다.")
        @Test
        void returnsFalse_whenStatusIsCompleted() {
            OrderModel order = createOrder();
            order.complete();

            assertThat(order.isPayable()).isFalse();
        }

        @DisplayName("취소 상태면, false를 반환한다.")
        @Test
        void returnsFalse_whenStatusIsCancelled() {
            OrderModel order = createOrder();
            order.cancel();

            assertThat(order.isPayable()).isFalse();
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
