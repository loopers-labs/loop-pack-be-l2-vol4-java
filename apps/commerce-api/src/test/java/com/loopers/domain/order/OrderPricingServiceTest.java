package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPricingServiceTest {

    private final OrderPricingService orderPricingService = new OrderPricingService();

    private OrderModel order;
    private ProductModel product;

    @BeforeEach
    void setUp() {
        BrandModel brand = new BrandModel("Nike", "스포츠 브랜드");
        product = new ProductModel(brand, "나이키 에어맥스", 150_000);
        ReflectionTestUtils.setField(product, "id", 10L);
        order = new OrderModel(1L);
    }

    @DisplayName("calculateTotal()을 호출할 때,")
    @Nested
    class CalculateTotal {

        @DisplayName("단일 항목의 총 금액이 단가 × 수량으로 계산된다.")
        @Test
        void calculatesSingleItemTotal_correctly() {
            // arrange
            OrderItemModel item = new OrderItemModel(order, 10L, "나이키 에어맥스", 150_000, "Nike", 3);

            // act
            int total = orderPricingService.calculateTotal(List.of(item));

            // assert
            assertThat(total).isEqualTo(450_000); // 150_000 × 3
        }

        @DisplayName("여러 항목의 총 금액이 각 항목 금액의 합산으로 계산된다.")
        @Test
        void accumulatesTotal_whenMultipleItems() {
            // arrange
            OrderItemModel item1 = new OrderItemModel(order, 10L, "나이키 에어맥스", 150_000, "Nike", 1);
            OrderItemModel item2 = new OrderItemModel(order, 20L, "아디다스 삼바", 120_000, "Adidas", 2);

            // act
            int total = orderPricingService.calculateTotal(List.of(item1, item2));

            // assert
            assertThat(total).isEqualTo(390_000); // 150_000 + 240_000
        }

        @DisplayName("항목이 없으면 총 금액은 0이다.")
        @Test
        void returnsZero_whenItemsIsEmpty() {
            // act
            int total = orderPricingService.calculateTotal(List.of());

            // assert
            assertThat(total).isEqualTo(0);
        }
    }
}
