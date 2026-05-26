package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ProductDetailService 단위 테스트.
 *
 * <p>Domain Service의 협력 행위(ProductService + BrandService 호출 및 조합)를
 * 검증한다. Spring 컨텍스트를 띄우지 않고 Mockito로 협력 객체를 대체한다.
 */
class ProductDetailServiceTest {

    private ProductService productService;
    private BrandService brandService;
    private ProductDetailService sut;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        brandService = mock(BrandService.class);
        sut = new ProductDetailService(productService, brandService);
    }

    @DisplayName("상품 상세를 조립할 때,")
    @Nested
    class Assemble {

        @DisplayName("정상적인 상품 ID가 주어지면 Product + Brand 묶음을 반환한다.")
        @Test
        void returnsProductWithBrand_whenValidProductId() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");
            when(productService.getProduct(100L)).thenReturn(product);
            when(brandService.getBrand(1L)).thenReturn(brand);

            // act
            ProductWithBrand result = sut.assemble(100L);

            // assert
            assertAll(
                () -> assertThat(result.product()).isSameAs(product),
                () -> assertThat(result.brand()).isSameAs(brand),
                () -> assertThat(result.brand().getName()).isEqualTo("나이키")
            );
        }

        @DisplayName("상품이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            when(productService.getProduct(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품 없음"));

            // act
            CoreException result = assertThrows(CoreException.class, () -> sut.assemble(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("상품의 브랜드가 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            ProductModel product = new ProductModel(99L, "신발", "러닝화", 50_000L);
            when(productService.getProduct(100L)).thenReturn(product);
            when(brandService.getBrand(99L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드 없음"));

            // act
            CoreException result = assertThrows(CoreException.class, () -> sut.assemble(100L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
