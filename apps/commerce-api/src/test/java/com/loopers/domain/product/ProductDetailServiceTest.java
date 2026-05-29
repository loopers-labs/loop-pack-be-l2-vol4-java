package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductDetailServiceTest {

    private final ProductService productService = mock(ProductService.class);
    private final BrandService brandService = mock(BrandService.class);
    private final ProductDetailService productDetailService = new ProductDetailService(productService, brandService);

    @DisplayName("상품 상세를 조회하면, 상품과 브랜드 정보를 조합해 반환한다.")
    @Test
    void combinesProductAndBrand() {
        // arrange
        ProductModel product = new ProductModel(5L, "에어맥스", "운동화", 1000L, 10);
        BrandModel brand = new BrandModel("나이키", "Just Do It");
        when(productService.getProduct(1L)).thenReturn(product);
        when(brandService.getBrand(5L)).thenReturn(brand);

        // act
        ProductDetail detail = productDetailService.getProductDetail(1L);

        // assert
        assertAll(
            () -> assertThat(detail.product()).isSameAs(product),
            () -> assertThat(detail.brand()).isSameAs(brand)
        );
        verify(brandService).getBrand(5L);
    }

    @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 전파되고 브랜드는 조회하지 않는다.")
    @Test
    void throwsNotFound_whenProductMissing() {
        // arrange
        when(productService.getProduct(1L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품 없음"));

        // act
        CoreException ex = assertThrows(CoreException.class, () -> productDetailService.getProductDetail(1L));

        // assert
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        verify(brandService, never()).getBrand(any());
    }
}
