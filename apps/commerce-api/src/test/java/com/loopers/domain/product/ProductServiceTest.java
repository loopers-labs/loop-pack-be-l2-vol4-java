package com.loopers.domain.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.ProductCursorCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductService 순수 단위 테스트 — Repository를 mock으로 격리해 DB 없이
 * 활성/비활성 분기, 좋아요 카운터 동기 +/-, soft delete 흐름을 검증한다.
 * (재고는 독립 Aggregate로 분리되어 StockService/StockServiceTest가 담당)
 */
class ProductServiceTest {

    private static final Long BRAND_ID = 5L;
    private static final Long PRODUCT_ID = 10L;

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository, new ProductCursorCodec(new ObjectMapper()));
    }

    private static ProductModel active(Long id, long likes) {
        return ProductModel.reconstitute(id, BRAND_ID, "상품" + id, "설명", null, 10000L, likes, null);
    }

    private static ProductModel inactive(Long id) {
        return ProductModel.reconstitute(id, BRAND_ID, "상품" + id, "설명", null, 10000L, 0L,
                ZonedDateTime.now());
    }

    @Nested
    @DisplayName("활성 상품 조회")
    class GetActive {

        @DisplayName("활성 상품이면 그대로 반환한다.")
        @Test
        void given_active_when_getActiveProduct_then_returns() {
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(active(PRODUCT_ID, 0L)));

            ProductModel result = productService.getActiveProduct(PRODUCT_ID);

            assertThat(result.getId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("비활성 상품이면 NOT_FOUND를 던진다(정보 노출 방지).")
        @Test
        void given_inactive_when_getActiveProduct_then_notFound() {
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(inactive(PRODUCT_ID)));

            Throwable thrown = catchThrowable(() -> productService.getActiveProduct(PRODUCT_ID));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND를 던진다.")
        @Test
        void given_missing_when_getActiveProduct_then_notFound() {
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> productService.getActiveProduct(PRODUCT_ID));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("findActive는 비활성/부재면 Optional.empty를 반환한다.")
        @Test
        void given_inactiveOrMissing_when_findActive_then_empty() {
            when(productRepository.find(1L)).thenReturn(Optional.of(inactive(1L)));
            when(productRepository.find(2L)).thenReturn(Optional.empty());

            assertThat(productService.findActive(1L)).isEmpty();
            assertThat(productService.findActive(2L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("좋아요 수 동기 카운터")
    class LikesCounter {

        @DisplayName("증가 시 원자적 +1 UPDATE에 위임한다(동시 좋아요 lost update 차단).")
        @Test
        void given_product_when_increaseLikesCount_then_delegatesAtomicIncrement() {
            productService.increaseLikesCount(PRODUCT_ID);

            verify(productRepository).incrementLikesCount(PRODUCT_ID);
        }

        @DisplayName("감소 시 원자적 -1 UPDATE에 위임한다(음수 방지는 영속 계층 가드).")
        @Test
        void given_product_when_decreaseLikesCount_then_delegatesAtomicDecrement() {
            productService.decreaseLikesCount(PRODUCT_ID);

            verify(productRepository).decrementLikesCount(PRODUCT_ID);
        }
    }

    @Nested
    @DisplayName("soft delete")
    class Delete {

        @DisplayName("활성 상품을 soft delete 하면 deletedAt이 채워진 채로 저장한다.")
        @Test
        void given_active_when_deleteProduct_then_savesInactive() {
            ProductModel product = active(PRODUCT_ID, 0L);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productRepository.save(any(ProductModel.class))).thenAnswer(inv -> inv.getArgument(0));

            productService.deleteProduct(PRODUCT_ID);

            assertThat(product.isActive()).isFalse();
            verify(productRepository).save(product);
        }
    }

    @Nested
    @DisplayName("활성 상품 batch 조회")
    class FindActiveByIds {

        @DisplayName("Repository 반환을 그대로 위임한다.")
        @Test
        void delegates_findActiveByIds() {
            List<Long> ids = List.of(1L, 2L, 3L);
            List<ProductModel> expected = List.of(active(1L, 0L), active(2L, 0L));
            when(productRepository.findActiveByIds(ids)).thenReturn(expected);

            List<ProductModel> result = productService.findActiveByIds(ids);

            assertThat(result).isSameAs(expected);
        }
    }
}
