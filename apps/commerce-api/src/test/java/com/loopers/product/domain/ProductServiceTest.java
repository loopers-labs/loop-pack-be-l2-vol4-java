package com.loopers.product.domain;

import com.loopers.brand.domain.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceTest {

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService();
    }

    @DisplayName("getOrThrow를 호출할 때,")
    @Nested
    class GetOrThrow {

        @DisplayName("product가 존재하면, 해당 product를 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, 1L);

            // act
            ProductModel result = productService.getOrThrow(Optional.of(product));

            // assert
            assertThat(result).isEqualTo(product);
        }

        @DisplayName("product가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                productService.getOrThrow(Optional.empty())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("resolveBrandName을 호출할 때,")
    @Nested
    class ResolveBrandName {

        @DisplayName("BrandModel이 존재하면, 브랜드명을 반환한다.")
        @Test
        void returnsBrandName_whenBrandExists() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");

            // act
            String result = productService.resolveBrandName(Optional.of(brand));

            // assert
            assertThat(result).isEqualTo("나이키");
        }

        @DisplayName("BrandModel이 존재하지 않으면, null을 반환한다.")
        @Test
        void returnsNull_whenBrandNotExists() {
            // act
            String result = productService.resolveBrandName(Optional.empty());

            // assert
            assertThat(result).isNull();
        }
    }
}
