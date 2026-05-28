package com.loopers.domain.order;

import com.loopers.domain.money.Money;
import com.loopers.domain.quantity.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {
    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("필수 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsOrder_whenRequiredInfoIsProvided() {
            // arrange
            Long userId = 1L;
            OrderStatus status = OrderStatus.COMPLETED;
            Money totalAmount = new Money(BigDecimal.valueOf(3000));

            // act
            Order order = new Order(userId, status, totalAmount);

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(userId),
                () -> assertThat(order.getStatus()).isEqualTo(status),
                () -> assertThat(order.getTotalAmount()).isEqualTo(totalAmount)
            );
        }
    }

    @DisplayName("주문을 접수할 때, ")
    @Nested
    class Place {
        @DisplayName("items의 lineAmount 합으로 totalAmount가 계산되고, 상태는 COMPLETED 다.")
        @Test
        void calculatesTotalAmount_fromItems() {
            // arrange
            Long userId = 1L;
            List<OrderItem> items = List.of(
                new OrderItem(10L, "에어맥스", new Money(BigDecimal.valueOf(1000)), new Quantity(2)),
                new OrderItem(11L, "양말", new Money(BigDecimal.valueOf(500)), new Quantity(1))
            );

            // act
            Order order = Order.place(userId, items);

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(userId),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED),
                () -> assertThat(order.getTotalAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500))
            );
        }

        @DisplayName("items가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenItemsAreEmpty() {
            // arrange
            Long userId = 1L;
            List<OrderItem> items = List.of();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Order.place(userId, items);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
