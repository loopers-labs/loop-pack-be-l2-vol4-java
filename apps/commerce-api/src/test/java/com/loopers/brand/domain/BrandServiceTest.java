package com.loopers.brand.domain;

import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandServiceTest {

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService();
    }

    @DisplayName("getOrThrow를 호출할 때,")
    @Nested
    class GetOrThrow {

        @DisplayName("brand가 존재하면, 해당 brand를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");

            // act
            BrandModel result = brandService.getOrThrow(Optional.of(brand));

            // assert
            assertThat(result).isEqualTo(brand);
        }

        @DisplayName("brand가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                brandService.getOrThrow(Optional.empty())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("deleteCascade를 호출할 때,")
    @Nested
    class DeleteCascade {

        @DisplayName("정상 요청이면, brand와 모든 products가 소프트 딜리트된다.")
        @Test
        void softDeletesBrandAndProducts_whenCalled() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");
            ProductModel product1 = new ProductModel("에어맥스", "나이키 운동화", 100000L, null);
            ProductModel product2 = new ProductModel("에어포스", "나이키 운동화", 120000L, null);

            // act
            brandService.deleteCascade(brand, List.of(product1, product2));

            // assert
            assertAll(
                () -> assertThat(brand.getDeletedAt()).isNotNull(),
                () -> assertThat(product1.getDeletedAt()).isNotNull(),
                () -> assertThat(product2.getDeletedAt()).isNotNull()
            );
        }
    }
}
