package com.loopers.domain.product;

import com.loopers.domain.product.enums.ProductStatus;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private static final Long BRAND_ID = 1L;
    private static final String VALID_NAME = "테스트상품";

    @DisplayName("상품 모델 생성 시,")
    @Nested
    class Create {

        static Stream<String> invalidNames() {
            return Stream.of("", " ", "가", "a".repeat(201));
        }

        static Stream<String> validNames() {
            return Stream.of("가나", VALID_NAME, "a".repeat(200));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("invalidNames")
        @DisplayName("상품명이 공백이거나 2자 미만 또는 200자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenNameIsOutOfRange(String invalidName) {
            CoreException result = assertThrows(CoreException.class,
                    () -> new ProductModel(BRAND_ID, new ProductName(invalidName)));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("validNames")
        @DisplayName("상품명이 2자 이상 200자 이하이면, ACTIVE 상태로 생성된다.")
        void createsProduct_whenNameIsValid(String validName) {
            ProductModel result = new ProductModel(BRAND_ID, new ProductName(validName));

            assertThat(result.getName()).isEqualTo(validName);
            assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }
    }

    @DisplayName("상품 삭제 시,")
    @Nested
    class Delete {

        @DisplayName("삭제하면, 상태가 INACTIVE 로 변경된다.")
        @Test
        void deactivatesProduct_whenDeleted() {
            ProductModel product = new ProductModel(BRAND_ID, new ProductName(VALID_NAME));

            product.delete();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
            assertThat(product.getDeletedAt()).isNotNull();
        }
    }

}
