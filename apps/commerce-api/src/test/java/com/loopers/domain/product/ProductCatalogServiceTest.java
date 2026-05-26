package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductCatalogServiceTest {

    private ProductCatalogService productCatalogService;

    @BeforeEach
    void setUp() {
        productCatalogService = new ProductCatalogService();
    }

    @DisplayName("상품 상세를 구성할 때, ")
    @Nested
    class GetProductDetail {
        @DisplayName("상품과 브랜드 정보를 조합한다.")
        @Test
        void returnsProductDetailWithBrand() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            BrandModel brand = new BrandModel("Loopers", "감성 이커머스 브랜드");

            // act
            ProductDetail result = productCatalogService.getProductDetail(product, brand);

            // assert
            assertAll(
                () -> assertThat(result.product()).isSameAs(product),
                () -> assertThat(result.brand()).isSameAs(brand)
            );
        }
    }

    @DisplayName("상품 목록을 구성할 때, ")
    @Nested
    class GetProductDetails {
        @DisplayName("각 상품의 브랜드 ID에 해당하는 브랜드 정보를 조합한다.")
        @Test
        void returnsProductDetailsWithBrand() {
            // arrange
            ProductModel firstProduct = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductModel secondProduct = new ProductModel(20L, "셔츠", "가벼운 셔츠", 20_000L, 5);
            BrandModel firstBrand = new BrandModel("Loopers", "감성 이커머스 브랜드");
            BrandModel secondBrand = new BrandModel("Daily", "데일리 브랜드");

            // act
            List<ProductDetail> results = productCatalogService.getProductDetails(
                List.of(firstProduct, secondProduct),
                Map.of(10L, firstBrand, 20L, secondBrand)
            );

            // assert
            assertAll(
                () -> assertThat(results).hasSize(2),
                () -> assertThat(results.get(0).product()).isSameAs(firstProduct),
                () -> assertThat(results.get(0).brand()).isSameAs(firstBrand),
                () -> assertThat(results.get(1).product()).isSameAs(secondProduct),
                () -> assertThat(results.get(1).brand()).isSameAs(secondBrand)
            );
        }

        @DisplayName("상품의 브랜드 정보를 찾을 수 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productCatalogService.getProductDetails(List.of(product), Map.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
