package com.loopers.application.like;

import com.loopers.application.product.ProductCacheService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @Mock
    private LikeService likeService;

    @Mock
    private ProductService productService;

    @Mock
    private ProductCacheService productCacheService;

    @InjectMocks
    private LikeFacade likeFacade;

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("새 좋아요가 등록되면 상품의 likeCount 를 증가시키고 캐시를 무효화한다.")
        @Test
        void increments_like_count_and_evicts_cache_when_new_like() {
            when(likeService.like(1L, 2L)).thenReturn(true);

            likeFacade.like(1L, 2L);

            verify(productService).incrementLikeCount(2L);
            verify(productCacheService).evictProductDetail(2L);
            verify(productCacheService).evictAllProductLists();
        }

        @DisplayName("이미 좋아요한 상품이면 likeCount 와 캐시를 변경하지 않는다. (멱등)")
        @Test
        void does_not_change_anything_when_already_liked() {
            when(likeService.like(1L, 2L)).thenReturn(false);

            likeFacade.like(1L, 2L);

            verify(productService, never()).incrementLikeCount(2L);
            verify(productCacheService, never()).evictProductDetail(2L);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 취소되면 상품의 likeCount 를 감소시키고 캐시를 무효화한다.")
        @Test
        void decrements_like_count_and_evicts_cache_when_cancelled() {
            when(likeService.unlike(1L, 2L)).thenReturn(true);

            likeFacade.unlike(1L, 2L);

            verify(productService).decrementLikeCount(2L);
            verify(productCacheService).evictProductDetail(2L);
            verify(productCacheService).evictAllProductLists();
        }

        @DisplayName("활성 좋아요가 없으면 likeCount 와 캐시를 변경하지 않는다. (멱등)")
        @Test
        void does_not_change_anything_when_no_active_like() {
            when(likeService.unlike(1L, 2L)).thenReturn(false);

            likeFacade.unlike(1L, 2L);

            verify(productService, never()).decrementLikeCount(2L);
            verify(productCacheService, never()).evictProductDetail(2L);
        }
    }
}
