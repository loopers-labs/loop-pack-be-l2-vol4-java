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

        @DisplayName("상태는 CREATED로 기본 설정되고 회원 식별자·주문 시각·세 금액·적용 쿠폰 식별자가 보존된다.")
        @Test
        void createsWithCreatedStatus_andPreservesFields() {
            // arrange
            ZonedDateTime orderedAt = ZonedDateTime.now();

            // act
            OrderModel order = OrderModel.builder()
                .userId(1L)
                .orderedAt(orderedAt)
                .originalAmount(35_000)
                .discountAmount(5_000)
                .finalAmount(30_000)
                .userCouponId(10L)
                .build();

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(order.getOrderedAt()).isEqualTo(orderedAt),
                () -> assertThat(order.getOriginalAmount()).isEqualTo(35_000),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(5_000),
                () -> assertThat(order.getFinalAmount()).isEqualTo(30_000),
                () -> assertThat(order.getUserCouponId()).isEqualTo(10L)
            );
        }

        @DisplayName("쿠폰 미적용 주문은 할인 금액 0과 비어 있는 적용 쿠폰 식별자로 생성된다.")
        @Test
        void createsWithoutCoupon() {
            // arrange & act
            OrderModel order = OrderModel.builder()
                .userId(1L)
                .orderedAt(ZonedDateTime.now())
                .originalAmount(35_000)
                .discountAmount(0)
                .finalAmount(35_000)
                .build();

            // assert
            assertAll(
                () -> assertThat(order.getDiscountAmount()).isZero(),
                () -> assertThat(order.getFinalAmount()).isEqualTo(35_000),
                () -> assertThat(order.getUserCouponId()).isNull()
            );
        }
    }
}
