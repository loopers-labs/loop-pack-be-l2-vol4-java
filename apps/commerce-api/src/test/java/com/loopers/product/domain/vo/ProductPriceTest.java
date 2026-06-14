package com.loopers.product.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPriceTest {

    @DisplayName("0 이상의 금액이 주어지면, 상품 가격을 생성한다.")
    @Test
    void createsProductPrice_whenValueIsNotNegative() {
        // arrange
        long value = 1_550_000L;

        // act
        ProductPrice price = ProductPrice.of(value);

        // assert
        assertThat(price.value()).isEqualTo(value);
    }

    @DisplayName("상품 가격이 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNegative() {
        // arrange
        long value = -1L;

        // act & assert
        assertThatThrownBy(() -> ProductPrice.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
