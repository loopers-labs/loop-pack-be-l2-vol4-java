package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemModelTest {

    private OrderModel order;

    @BeforeEach
    void setUp() {
        order = new OrderModel(1L);
    }

    @DisplayName("OrderItemModel 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 항목으로 생성 시 totalPrice가 unitPrice * quantity이다.")
        @Test
        void calculatesTotalPrice_whenValidItemCreated() {
            // act
            OrderItemModel item = new OrderItemModel(order, 10L, "나이키 에어맥스", 150_000, "Nike", 2);

            // assert
            assertThat(item.totalPrice()).isEqualTo(300_000);
            assertThat(item.getQuantity()).isEqualTo(2);
        }

        @DisplayName("quantity가 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(order, 10L, "상품", 10_000, "브랜드", 0)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(order, 10L, "상품", 10_000, "브랜드", -1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("addItem()을 호출할 때,")
    @Nested
    class AddItem {

        @DisplayName("항목이 items 컬렉션에 추가된다.")
        @Test
        void addsItemToList_whenCalled() {
            // arrange
            OrderItemModel item1 = new OrderItemModel(order, 10L, "상품A", 10_000, "브랜드", 2);
            OrderItemModel item2 = new OrderItemModel(order, 11L, "상품B", 20_000, "브랜드", 1);

            // act
            order.addItem(item1);
            order.addItem(item2);

            // assert — 총액 계산은 OrderPricingService 담당, addItem은 컬렉션 관리만 수행
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualTo(0);
        }
    }
}
