package com.loopers.product.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductNameTest {

    @DisplayName("유효한 값이 주어지면, 상품명을 생성한다.")
    @Test
    void createsProductName_whenValueIsValid() {
        // arrange
        String value = "아이폰 16 Pro";

        // act
        ProductName name = ProductName.of(value);

        // assert
        assertThat(name.value()).isEqualTo(value);
    }

    @DisplayName("값이 비어있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsBlank() {
        // arrange
        String value = " ";

        // act & assert
        assertThatThrownBy(() -> ProductName.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
