package com.loopers.application.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.like.ProductLikeFacade;
import com.loopers.application.like.ProductLikeService;
import com.loopers.application.product.ProductCachePolicy;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.cache.RedisCacheRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductActivityFacadePublishingTest {

  @DisplayName("캐시된 상품 상세 조회에도 조회 이벤트를 발행한다.")
  @Test
  void publishesViewedEventOnCachedProductDetail() {
    // arrange
    ProductService productService = mock(ProductService.class);
    RedisCacheRepository cacheRepository = mock(RedisCacheRepository.class);
    ProductActivityEventPublisher eventPublisher = mock(ProductActivityEventPublisher.class);
    ProductFacade productFacade =
        new ProductFacade(productService, cacheRepository, eventPublisher);
    Long productId = 1L;
    ProductInfo cached = new ProductInfo(productId, "상품", "설명", 1000L, 10, 1L, 0L);
    when(cacheRepository.find(ProductCachePolicy.detailKey(productId), ProductInfo.class))
        .thenReturn(Optional.of(cached));

    // act
    productFacade.getProduct(productId);

    // assert
    verify(eventPublisher).publishViewed(productId);
  }

  @DisplayName("실제 신규 좋아요만 좋아요 이벤트를 발행한다.")
  @Test
  void publishesLikedEventOnlyWhenLikeIsNew() {
    // arrange
    ProductLikeService likeService = mock(ProductLikeService.class);
    RedisCacheRepository cacheRepository = mock(RedisCacheRepository.class);
    ProductActivityEventPublisher eventPublisher = mock(ProductActivityEventPublisher.class);
    ProductLikeFacade likeFacade =
        new ProductLikeFacade(likeService, cacheRepository, eventPublisher);
    Long productId = 1L;
    when(likeService.like("user-1", productId)).thenReturn(true, false);

    // act
    likeFacade.like("user-1", productId);
    likeFacade.like("user-1", productId);

    // assert
    verify(eventPublisher).publishLiked(productId);
  }

  @DisplayName("좋아요 취소는 좋아요 이벤트를 발행하지 않는다.")
  @Test
  void doesNotPublishEventWhenLikeIsCancelled() {
    // arrange
    ProductLikeService likeService = mock(ProductLikeService.class);
    RedisCacheRepository cacheRepository = mock(RedisCacheRepository.class);
    ProductActivityEventPublisher eventPublisher = mock(ProductActivityEventPublisher.class);
    ProductLikeFacade likeFacade =
        new ProductLikeFacade(likeService, cacheRepository, eventPublisher);
    Long productId = 1L;
    when(likeService.unlike("user-1", productId)).thenReturn(true);

    // act
    likeFacade.unlike("user-1", productId);

    // assert
    verify(eventPublisher, never()).publishLiked(productId);
  }
}
