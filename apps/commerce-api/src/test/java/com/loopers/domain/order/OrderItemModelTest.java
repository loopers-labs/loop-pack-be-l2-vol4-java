package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderItemModelTest {

    @DisplayName("OrderItem을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("주문 시점 상품 정보와 수량을 스냅샷으로 보존한다.")
        @Test
        void preservesSnapshot() {
            // act
            OrderItemModel orderItem = OrderItemModel.builder()
                .productId(1L)
                .productName("감성 가디건")
                .productBrandName("감성 브랜드")
                .unitPrice(39_000)
                .rawQuantity(2)
                .build();

            // assert
            assertAll(
                () -> assertThat(orderItem.getProductId()).isEqualTo(1L),
                () -> assertThat(orderItem.getProductName()).isEqualTo("감성 가디건"),
                () -> assertThat(orderItem.getProductBrandName()).isEqualTo("감성 브랜드"),
                () -> assertThat(orderItem.getUnitPrice()).isEqualTo(39_000),
                () -> assertThat(orderItem.getQuantity().value()).isEqualTo(2)
            );
        }
    }

    @DisplayName("OrderItem의 합계를 계산할 때,")
    @Nested
    class TotalPrice {

        @DisplayName("단가와 수량을 곱한 값을 반환한다.")
        @Test
        void returnsUnitPriceTimesQuantity() {
            // arrange
            OrderItemModel orderItem = OrderItemModel.builder()
                .productId(1L)
                .productName("감성 가디건")
                .productBrandName("감성 브랜드")
                .unitPrice(39_000)
                .rawQuantity(3)
                .build();

            // act
            int totalPrice = orderItem.totalPrice();

            // assert
            assertThat(totalPrice).isEqualTo(117_000);
        }
    }

    @DisplayName("OrderItem에 주문 식별자를 배정할 때,")
    @Nested
    class AssignOrder {

        @DisplayName("주어진 주문 식별자가 항목에 설정된다.")
        @Test
        void assignsOrderId() {
            // arrange
            OrderItemModel orderItem = OrderItemModel.builder()
                .productId(1L)
                .productName("감성 가디건")
                .productBrandName("감성 브랜드")
                .unitPrice(39_000)
                .rawQuantity(2)
                .build();

            // act
            orderItem.assignOrder(100L);

            // assert
            assertThat(orderItem.getOrderId()).isEqualTo(100L);
        }
    }
}
