package com.loopers.domain.order;

import com.loopers.domain.order.vo.Money;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.StockQuantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultOrderTotalPolicyTest {

    private DefaultOrderTotalPolicy sut;

    @BeforeEach
    void setUp() {
        sut = new DefaultOrderTotalPolicy();
    }

    private OrderItemModel createItem(OrderModel order, long price, int quantity) {
        return new OrderItemModel(order, 1L, 1L, new ProductName("상품"), new Price(price), new StockQuantity(quantity));
    }

    @DisplayName("총액 계산(calculate) 시,")
    @Nested
    class Calculate {

        @DisplayName("단일 아이템이면, 가격 × 수량이 반환된다.")
        @Test
        void returnsTotal_whenSingleItem() {
            OrderModel order = new OrderModel(1L);
            order.addItem(createItem(order, 10000L, 3));

            Money result = sut.calculate(order.getItems());

            assertThat(result.getValue()).isEqualTo(30000L);
        }

        @DisplayName("여러 아이템이면, 각 (가격 × 수량)의 합산이 반환된다.")
        @Test
        void returnsSum_whenMultipleItems() {
            OrderModel order = new OrderModel(1L);
            order.addItem(createItem(order, 10000L, 2));
            order.addItem(createItem(order, 5000L, 4));

            Money result = sut.calculate(order.getItems());

            assertThat(result.getValue()).isEqualTo(40000L);
        }

        @DisplayName("아이템이 없으면, 0이 반환된다.")
        @Test
        void returnsZero_whenNoItems() {
            OrderModel order = new OrderModel(1L);

            Money result = sut.calculate(order.getItems());

            assertThat(result.getValue()).isEqualTo(0L);
        }
    }
}
