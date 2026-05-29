package com.loopers.domain.order.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemSnapshotTest {

    @DisplayName("주문 시점의 브랜드와 상품 정보가 주어지면, 주문 항목 스냅샷을 생성한다.")
    @Test
    void createsOrderItemSnapshot_whenBrandAndProductInfoAreProvided() {
        // arrange
        Long brandId = 1L;
        String brandName = "애플";
        Long productId = 1L;
        String productName = "아이폰 16 Pro";

        // act
        OrderItemSnapshot snapshot = OrderItemSnapshot.of(brandId, brandName, productId, productName);

        // assert
        assertThat(snapshot.brandId()).isEqualTo(brandId);
        assertThat(snapshot.brandName()).isEqualTo(brandName);
        assertThat(snapshot.productId()).isEqualTo(productId);
        assertThat(snapshot.productName()).isEqualTo(productName);
    }

    @DisplayName("주문 항목 스냅샷의 필수값이 비어있으면, BAD_REQUEST 예외를 던진다.")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSnapshots")
    void throwsBadRequest_whenRequiredSnapshotFieldIsMissing(
        String caseName,
        Long brandId,
        String brandName,
        Long productId,
        String productName
    ) {
        // act & assert
        assertThatThrownBy(() -> OrderItemSnapshot.of(brandId, brandName, productId, productName))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    private static Stream<Arguments> invalidSnapshots() {
        return Stream.of(
            Arguments.of("브랜드 ID가 null", null, "애플", 1L, "아이폰 16 Pro"),
            Arguments.of("브랜드명이 blank", 1L, " ", 1L, "아이폰 16 Pro"),
            Arguments.of("상품 ID가 null", 1L, "애플", null, "아이폰 16 Pro"),
            Arguments.of("상품명이 blank", 1L, "애플", 1L, " ")
        );
    }
}
