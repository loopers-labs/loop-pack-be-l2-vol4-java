package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductFacadeTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final ProductFacade productFacade = new ProductFacade(productRepository, brandRepository);

    @DisplayName("상품 상세를 조회할 때, ")
    @Nested
    class GetProductDetail {

        @DisplayName("상품과 브랜드를 조합해 브랜드명·좋아요 수를 포함한 상세를 반환한다.")
        @Test
        void combinesProductAndBrand() {
            // arrange
            ProductModel product = new ProductModel(5L, "에어맥스", "운동화", 1000L, 10);
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            when(productRepository.find(1L)).thenReturn(Optional.of(product));
            when(brandRepository.find(5L)).thenReturn(Optional.of(brand));

            // act
            ProductDetailInfo info = productFacade.getProductDetail(1L);

            // assert
            assertAll(
                () -> assertThat(info.name()).isEqualTo("에어맥스"),
                () -> assertThat(info.brandName()).isEqualTo("나이키"),
                () -> assertThat(info.price()).isEqualTo(1000L),
                () -> assertThat(info.likeCount()).isEqualTo(0)
            );
            verify(brandRepository).find(5L);
        }

        @DisplayName("상품이 없으면 NOT_FOUND 이고 브랜드는 조회하지 않는다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            // arrange
            when(productRepository.find(1L)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class, () -> productFacade.getProductDetail(1L));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(brandRepository, never()).find(any());
        }
    }
}
