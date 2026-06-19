package com.loopers.application.like;

import com.loopers.application.product.ProductCacheRepository;
import com.loopers.application.product.ProductLikeCountRepository;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLikeFacadeTest {

    @Mock
    private ProductLikeService productLikeService;

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private ProductBrandProcessService productBrandProcessService;

    @Mock
    private ProductCacheRepository productCacheRepository;

    @Mock
    private ProductLikeCountRepository productLikeCountRepository;

    @DisplayName("Redis 카운터 갱신에 성공하면 상품 row lock 없이 좋아요 수를 반영한다.")
    @Test
    void increasesRedisLikeCount_withoutProductRowLock_whenRedisCounterSucceeds() {
        // arrange
        ProductLikeFacade productLikeFacade = createFacade();
        Product product = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 10, 3, false);
        when(productService.getProduct(1L)).thenReturn(product);
        when(productLikeService.likeProduct("user1234", 1L)).thenReturn(true);
        when(productLikeCountRepository.increase(1L, 3)).thenReturn(true);

        // act
        productLikeFacade.likeProduct("user1234", 1L);

        // assert
        verify(productService, never()).findProductsByIdsForUpdate(any());
        verify(productService, never()).saveProducts(any());
        verify(productCacheRepository).evictProduct(1L);
        verify(productCacheRepository).evictProductLists();
    }

    @DisplayName("Redis 카운터 갱신에 실패하면 DB row lock 기반 좋아요 수 갱신으로 폴백한다.")
    @Test
    void fallsBackToDbLikeCountUpdate_whenRedisCounterFails() {
        // arrange
        ProductLikeFacade productLikeFacade = createFacade();
        Product product = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 10, 3, false);
        Product lockedProduct = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 10, 3, false);
        when(productService.getProduct(1L)).thenReturn(product);
        when(productLikeService.likeProduct("user1234", 1L)).thenReturn(true);
        when(productLikeCountRepository.increase(1L, 3)).thenReturn(false);
        when(productService.findProductsByIdsForUpdate(List.of(1L))).thenReturn(List.of(lockedProduct));

        // act
        productLikeFacade.likeProduct("user1234", 1L);

        // assert
        assertThat(lockedProduct.getLikeCount()).isEqualTo(4);
        verify(productService).saveProducts(List.of(lockedProduct));
        verify(productCacheRepository).evictProduct(1L);
        verify(productCacheRepository).evictProductLists();
    }

    private ProductLikeFacade createFacade() {
        return new ProductLikeFacade(
            productLikeService,
            productService,
            brandService,
            productBrandProcessService,
            productCacheRepository,
            productLikeCountRepository
        );
    }
}
