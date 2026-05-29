package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long ABSENT_PRODUCT_ID = 999L;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @DisplayName("활성 상품 조회 시, ")
    @Nested
    class GetActive {

        @DisplayName("존재하는 active 상품이면, 해당 상품을 반환한다.")
        @Test
        void returnsProduct_whenActiveProductExists() {
            // given
            Long productId = 1L;
            ProductModel product = new ProductModel("에어포스1", "흰색 운동화", 139_000L, 10L);
            given(productRepository.findActive(productId)).willReturn(Optional.of(product));

            // when
            ProductModel result = productService.getActive(productId);

            // then
            assertThat(result).isSameAs(product);
        }

        @DisplayName("존재하지 않거나 soft-deleted 상품이면, PRODUCT_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsProductNotFound_whenProductIsAbsentOrSoftDeleted() {
            // given
            Long productId = 999L;
            given(productRepository.findActive(productId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> productService.getActive(productId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);
        }
    }

    @DisplayName("브랜드 단위로 상품을 삭제할 때, ")
    @Nested
    class DeleteByBrandId {

        @DisplayName("해당 브랜드의 활성 상품들이 모두 soft-delete 된다.")
        @Test
        void softDeletesAllActiveProductsOfBrand() {
            // given
            Long brandId = 1L;
            ProductModel airmax = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L, brandId);
            ProductModel chuck  = new ProductModel("척테일러", "캔버스 클래식", 79_000L, brandId);
            given(productRepository.findAllActiveByBrandId(brandId))
                .willReturn(List.of(airmax, chuck));

            // when
            productService.deleteByBrandId(brandId);

            // then
            assertAll(
                () -> assertThat(airmax.getDeletedAt()).isNotNull(),
                () -> assertThat(chuck.getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("해당 브랜드의 활성 상품이 없으면, 아무 동작도 하지 않는다.")
        @Test
        void doesNothing_whenNoActiveProductsExist() {
            // given
            Long brandId = 1L;
            given(productRepository.findAllActiveByBrandId(brandId)).willReturn(List.of());

            // when & then — 예외가 발생하지 않는다
            productService.deleteByBrandId(brandId);
        }
    }

    @DisplayName("좋아요 수 증가 시, ")
    @Nested
    class IncrementLikeCount {

        @DisplayName("active 상품이 존재하면 정상적으로 종료된다.")
        @Test
        void doesNotThrow_whenActiveProductExists() {
            // given
            given(productRepository.incrementLikeCount(PRODUCT_ID)).willReturn(1);

            // when & then — 예외가 발생하지 않는다
            productService.incrementLikeCount(PRODUCT_ID);
        }

        @DisplayName("active 상품이 없으면 PRODUCT_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsProductNotFound_whenAffectedRowsIsZero() {
            // given
            given(productRepository.incrementLikeCount(ABSENT_PRODUCT_ID)).willReturn(0);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> productService.incrementLikeCount(ABSENT_PRODUCT_ID));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);
        }
    }

    @DisplayName("좋아요 수 감소 시, ")
    @Nested
    class DecrementLikeCount {

        @DisplayName("active 상품이 존재하면 정상적으로 종료된다.")
        @Test
        void doesNotThrow_whenActiveProductExists() {
            // given
            given(productRepository.decrementLikeCount(PRODUCT_ID)).willReturn(1);

            // when & then — 예외가 발생하지 않는다
            productService.decrementLikeCount(PRODUCT_ID);
        }

        @DisplayName("active 상품이 없으면 PRODUCT_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsProductNotFound_whenAffectedRowsIsZero() {
            // given
            given(productRepository.decrementLikeCount(ABSENT_PRODUCT_ID)).willReturn(0);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> productService.decrementLikeCount(ABSENT_PRODUCT_ID));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);
        }
    }
}
