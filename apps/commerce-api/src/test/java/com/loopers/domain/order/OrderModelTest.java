package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderModelTest {

    @DisplayName("Order를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("상태는 CREATED로 기본 설정되고 회원 식별자·주문 시각·총 결제 금액이 보존된다.")
        @Test
        void createsWithCreatedStatus_andPreservesFields() {
            // arrange
            ZonedDateTime orderedAt = ZonedDateTime.now();

            // act
            OrderModel order = OrderModel.builder()
                .userId(1L)
                .orderedAt(orderedAt)
                .totalPrice(35_000)
                .build();

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(order.getOrderedAt()).isEqualTo(orderedAt),
                () -> assertThat(order.getTotalPrice()).isEqualTo(35_000)
            );
        }
    }
}
