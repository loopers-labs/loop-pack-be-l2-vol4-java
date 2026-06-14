package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderItemTest {

    @DisplayName("주문 당시 상품/브랜드 스냅샷과 수량이 주어지면, 주문 항목을 생성한다.")
    @Test
    void createsOrderItem_whenSnapshotAndQuantityAreProvided() {
        // arrange
        Long brandId = 1L;
        String brandName = "애플";
        Long productId = 1L;
        String productName = "아이폰 16 Pro";
        long unitPrice = 1_550_000L;
        int quantity = 2;

        // act
        OrderItem orderItem = OrderItem.create(brandId, brandName, productId, productName, unitPrice, quantity);

        // assert
        assertAll(
            () -> assertThat(orderItem.getBrandId()).isEqualTo(brandId),
            () -> assertThat(orderItem.getBrandName()).isEqualTo(brandName),
            () -> assertThat(orderItem.getProductId()).isEqualTo(productId),
            () -> assertThat(orderItem.getProductName()).isEqualTo(productName),
            () -> assertThat(orderItem.getUnitPrice()).isEqualTo(unitPrice),
            () -> assertThat(orderItem.getQuantity()).isEqualTo(quantity),
            () -> assertThat(orderItem.getTotalPrice()).isEqualTo(3_100_000L)
        );
    }

    @DisplayName("주문 수량이 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenQuantityIsNotPositive() {
        // arrange
        int quantity = 0;

        // act & assert
        assertThatThrownBy(() -> OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, quantity))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품 단가가 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUnitPriceIsNegative() {
        // arrange
        long unitPrice = -1L;

        // act & assert
        assertThatThrownBy(() -> OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", unitPrice, 1))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
