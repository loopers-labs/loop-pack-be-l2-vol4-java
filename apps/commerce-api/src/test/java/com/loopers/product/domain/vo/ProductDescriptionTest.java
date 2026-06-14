package com.loopers.product.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDescriptionTest {

    @DisplayName("유효한 값이 주어지면, 상품 설명을 생성한다.")
    @Test
    void createsProductDescription_whenValueIsValid() {
        // arrange
        String value = "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰";

        // act
        ProductDescription description = ProductDescription.of(value);

        // assert
        assertThat(description.value()).isEqualTo(value);
    }

    @DisplayName("값이 비어있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsBlank() {
        // arrange
        String value = " ";

        // act & assert
        assertThatThrownBy(() -> ProductDescription.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
