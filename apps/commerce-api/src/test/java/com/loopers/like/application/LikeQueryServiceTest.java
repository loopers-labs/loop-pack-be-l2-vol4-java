package com.loopers.like.application;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeQueryServiceTest {

    private static final Long USER_ID = 1L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final LikeQueryService likeQueryService = new LikeQueryService(likeRepository, productRepository);

    @Test
    @DisplayName("getMyLikes: 활성 like 의 productId 로 active product 들을 LikedProduct 로 매핑해 반환한다")
    void givenActiveLikes_whenGetMyLikes_thenReturnsLikedProducts() {
        Like l1 = Like.create(USER_ID, 10L);
        Like l2 = Like.create(USER_ID, 20L);
        Product p1 = Product.create(1L, "P1", "설명", 1000L, null);
        Product p2 = Product.create(1L, "P2", "설명", 2000L, null);
        when(likeRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(l1, l2));
        when(productRepository.findAllByIdIn(List.of(10L, 20L))).thenReturn(List.of(p1, p2));

        List<LikeResult.LikedProduct> result = likeQueryService.getMyLikes(USER_ID);

        assertThat(result)
                .extracting(LikeResult.LikedProduct::name)
                .containsExactly("P1", "P2");
    }

    @Test
    @DisplayName("getMyLikes: 활성 like 가 없으면 빈 리스트를 반환하고 product 조회를 안 한다")
    void givenNoActiveLikes_whenGetMyLikes_thenReturnsEmptyAndDoesNotQueryProducts() {
        when(likeRepository.findActiveByUserId(USER_ID)).thenReturn(List.of());

        List<LikeResult.LikedProduct> result = likeQueryService.getMyLikes(USER_ID);

        assertThat(result).isEmpty();
        verify(productRepository, never()).findAllByIdIn(any());
    }
}
