package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.event.ProductViewed;
import com.loopers.domain.productrank.ProductRankRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductFacadeViewEventTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final LikeCountRepository likeCountRepository = mock(LikeCountRepository.class);
    private final ProductRankRepository productRankRepository = mock(ProductRankRepository.class);
    private final ProductCache productCache = mock(ProductCache.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProductFacade productFacade = new ProductFacade(
        productRepository, brandRepository, likeCountRepository, productRankRepository, productCache, eventPublisher);

    @DisplayName("상세조회 시(캐시 미스) ProductViewed 이벤트를 발행한다.")
    @Test
    void publishesProductViewed_onCacheMiss() {
        when(productCache.getDetail(1L)).thenReturn(Optional.empty());
        Product product = new Product(9L, "에어맥스", "운동화", 1000L, 10);
        when(productRepository.find(1L)).thenReturn(Optional.of(product));
        when(brandRepository.find(9L)).thenReturn(Optional.of(new Brand("나이키", "Just Do It")));
        when(likeCountRepository.find(1L)).thenReturn(Optional.empty());

        productFacade.getProductDetail(1L);

        verify(eventPublisher).publishEvent(any(ProductViewed.class));
    }

    @DisplayName("상세조회 시(캐시 히트) 에도 ProductViewed 이벤트를 발행한다.")
    @Test
    void publishesProductViewed_onCacheHit() {
        ProductDetailInfo cached = ProductDetailInfo.from(
            new Product(9L, "에어맥스", "운동화", 1000L, 10),
            new Brand("나이키", "Just Do It"), 0L);
        when(productCache.getDetail(1L)).thenReturn(Optional.of(cached));

        productFacade.getProductDetail(1L);

        verify(eventPublisher).publishEvent(any(ProductViewed.class));
    }
}
