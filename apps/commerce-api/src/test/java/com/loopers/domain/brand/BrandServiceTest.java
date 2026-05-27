package com.loopers.domain.brand;

import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandServiceTest {

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService();
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class DeleteBrandWithProducts {

        @DisplayName("브랜드와 해당 브랜드의 상품을 함께 삭제 처리한다.")
        @Test
        void deletesBrandAndProducts() {
            // arrange
            BrandModel brand = new BrandModel("Loopers", "감성 이커머스 브랜드");
            ProductModel firstProduct = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductModel secondProduct = new ProductModel(10L, "셔츠", "가벼운 셔츠", 20_000L, 5);

            // act
            brandService.deleteBrandWithProducts(brand, List.of(firstProduct, secondProduct));

            // assert
            assertAll(
                () -> assertThat(brand.getDeletedAt()).isNotNull(),
                () -> assertThat(firstProduct.getDeletedAt()).isNotNull(),
                () -> assertThat(secondProduct.getDeletedAt()).isNotNull()
            );
        }
    }
}
