package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    private ProductCatalogService productCatalogService;

    @BeforeEach
    void setUp() {
        productCatalogService = new ProductCatalogService(productService, brandService);
    }

    @DisplayName("상품 상세를 조회할 때, ")
    @Nested
    class GetProductDetail {
        @DisplayName("상품의 브랜드 ID로 브랜드 정보를 함께 조회한다.")
        @Test
        void returnsProductDetailWithBrand() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            BrandModel brand = new BrandModel("Loopers", "감성 이커머스 브랜드");
            when(productService.getProduct(1L)).thenReturn(product);
            when(brandService.getBrand(10L)).thenReturn(brand);

            // act
            ProductDetail result = productCatalogService.getProductDetail(1L);

            // assert
            assertAll(
                () -> assertThat(result.product()).isSameAs(product),
                () -> assertThat(result.brand()).isSameAs(brand),
                () -> verify(brandService).getBrand(product.getBrandId())
            );
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetProductDetails {
        @DisplayName("각 상품의 브랜드 정보를 함께 조회한다.")
        @Test
        void returnsProductDetailsWithBrand() {
            // arrange
            ProductModel firstProduct = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductModel secondProduct = new ProductModel(20L, "셔츠", "가벼운 셔츠", 20_000L, 5);
            BrandModel firstBrand = new BrandModel("Loopers", "감성 이커머스 브랜드");
            BrandModel secondBrand = new BrandModel("Daily", "데일리 브랜드");
            when(productService.getAllProducts(ProductSort.LATEST)).thenReturn(List.of(firstProduct, secondProduct));
            when(brandService.getBrand(10L)).thenReturn(firstBrand);
            when(brandService.getBrand(20L)).thenReturn(secondBrand);

            // act
            List<ProductDetail> results = productCatalogService.getProductDetails(ProductSort.LATEST);

            // assert
            assertAll(
                () -> assertThat(results).hasSize(2),
                () -> assertThat(results.get(0).product()).isSameAs(firstProduct),
                () -> assertThat(results.get(0).brand()).isSameAs(firstBrand),
                () -> assertThat(results.get(1).product()).isSameAs(secondProduct),
                () -> assertThat(results.get(1).brand()).isSameAs(secondBrand),
                () -> verify(productService).getAllProducts(ProductSort.LATEST)
            );
        }
    }
}
