package com.loopers.domain.order;

import com.loopers.domain.money.Money;
import com.loopers.domain.quantity.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderItemTest {
    @DisplayName("주문 항목을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("스냅샷 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenSnapshotInfoIsProvided() {
            // arrange
            Long productId = 10L;
            String productName = "에어맥스";
            Money unitPrice = new Money(BigDecimal.valueOf(100000));
            Quantity quantity = new Quantity(2);

            // act
            OrderItem item = new OrderItem(productId, productName, unitPrice, quantity);

            // assert
            assertAll(
                () -> assertThat(item.getProductId()).isEqualTo(productId),
                () -> assertThat(item.getProductName()).isEqualTo(productName),
                () -> assertThat(item.getUnitPrice()).isEqualTo(unitPrice),
                () -> assertThat(item.getQuantity()).isEqualTo(quantity)
            );
        }
    }

    @DisplayName("주문 항목의 소계를 계산할 때, ")
    @Nested
    class LineAmount {
        @DisplayName("단가 × 수량 만큼의 Money를 반환한다.")
        @Test
        void returnsUnitPriceMultipliedByQuantity() {
            // arrange
            OrderItem item = new OrderItem(
                10L, "에어맥스",
                new Money(BigDecimal.valueOf(1000)),
                new Quantity(3)
            );

            // act
            Money result = item.lineAmount();

            // assert
            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }
    }
}
