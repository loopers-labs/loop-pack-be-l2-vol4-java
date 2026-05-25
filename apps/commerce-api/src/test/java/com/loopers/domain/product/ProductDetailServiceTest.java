package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDetailServiceTest {

    private static final Long PRODUCT_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private ProductDetailService productDetailService;

    @DisplayName("상품 상세 조합 시")
    @Nested
    class GetDetail {

        @DisplayName("상품과 브랜드가 모두 존재하면 ProductDetail로 조합해 반환한다")
        @Test
        void returnsProductDetail_whenBothExist() {
            // given
            BrandModel brand = new BrandModel("Loopers", "감성 라이프스타일 브랜드");
            ProductModel product = new ProductModel(brand, "후드", "포근함", 49_000L);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(brandRepository.findById(brand.getId())).thenReturn(Optional.of(brand));

            // when
            ProductDetail detail = productDetailService.getDetail(PRODUCT_ID);

            // then
            assertThat(detail.product()).isSameAs(product);
            assertThat(detail.brand()).isSameAs(brand);
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생하고 브랜드는 조회되지 않는다")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // given
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> productDetailService.getDetail(PRODUCT_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(brandRepository, never()).findById(any());
        }

        @DisplayName("브랜드가 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // given
            BrandModel brand = new BrandModel("Loopers", "감성 라이프스타일 브랜드");
            ProductModel product = new ProductModel(brand, "후드", "포근함", 49_000L);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(brandRepository.findById(brand.getId())).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> productDetailService.getDetail(PRODUCT_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
