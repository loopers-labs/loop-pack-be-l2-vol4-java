package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LikeService 순수 단위 테스트 — Repository/ProductService를 mock으로 격리해 DB 없이
 * 좋아요 등록/취소 흐름의 분기(신규/멱등/재활성)와 카운터 호출 여부를 검증한다.
 * (실제 영속·카운터 정합성은 LikeServiceIntegrationTest가 Testcontainers로 검증)
 */
class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private LikeRepository likeRepository;
    private ProductService productService;
    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        productService = mock(ProductService.class);
        likeService = new LikeService(likeRepository, productService);
    }

    @Nested
    @DisplayName("좋아요 등록")
    class Like {

        @DisplayName("좋아요한 적 없으면, 저장하고 likesCount를 1 증가시킨다.")
        @Test
        void given_noLike_when_like_then_savesAndIncrements() {
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

            likeService.like(USER_ID, PRODUCT_ID);

            verify(likeRepository).save(any(LikeModel.class));
            verify(productService).increaseLikesCount(PRODUCT_ID);
        }

        @DisplayName("이미 좋아요 상태면, 멱등하게 저장·카운터 증가를 하지 않는다.")
        @Test
        void given_alreadyLiked_when_like_then_idempotentNoOp() {
            LikeModel active = new LikeModel(USER_ID, PRODUCT_ID);
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));

            likeService.like(USER_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(productService, never()).increaseLikesCount(anyLong());
        }

        @DisplayName("취소된 좋아요가 있으면, 원자적으로 활성화하고 likesCount를 1 증가시킨다.")
        @Test
        void given_canceledLike_when_like_then_activatesAndIncrements() {
            LikeModel canceled = new LikeModel(USER_ID, PRODUCT_ID);
            canceled.delete();
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(canceled));
            when(likeRepository.activate(USER_ID, PRODUCT_ID)).thenReturn(1);   // 이 트랜잭션이 실제 전이

            likeService.like(USER_ID, PRODUCT_ID);

            verify(likeRepository).activate(USER_ID, PRODUCT_ID);
            verify(productService).increaseLikesCount(PRODUCT_ID);
        }

        @DisplayName("취소된 좋아요를 동시에 재활성하면, 실제 전이하지 못한 쪽(영향 행 0)은 카운터를 올리지 않는다.")
        @Test
        void given_canceledLike_when_activateAffectsNoRow_then_noIncrement() {
            LikeModel canceled = new LikeModel(USER_ID, PRODUCT_ID);
            canceled.delete();
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(canceled));
            when(likeRepository.activate(USER_ID, PRODUCT_ID)).thenReturn(0);   // 다른 트랜잭션이 먼저 전이

            likeService.like(USER_ID, PRODUCT_ID);

            verify(productService, never()).increaseLikesCount(anyLong());
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class Unlike {

        @DisplayName("활성 좋아요가 있으면, 원자적으로 비활성화하고 likesCount를 1 감소시킨다.")
        @Test
        void given_activeLike_when_unlike_then_deactivatesAndDecrements() {
            LikeModel active = new LikeModel(USER_ID, PRODUCT_ID);
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));
            when(likeRepository.deactivate(USER_ID, PRODUCT_ID)).thenReturn(1);

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(likeRepository).deactivate(USER_ID, PRODUCT_ID);
            verify(productService).decreaseLikesCount(PRODUCT_ID);
        }

        @DisplayName("활성 좋아요를 동시에 취소하면, 실제 전이하지 못한 쪽(영향 행 0)은 카운터를 내리지 않는다.")
        @Test
        void given_activeLike_when_deactivateAffectsNoRow_then_noDecrement() {
            LikeModel active = new LikeModel(USER_ID, PRODUCT_ID);
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));
            when(likeRepository.deactivate(USER_ID, PRODUCT_ID)).thenReturn(0);

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(productService, never()).decreaseLikesCount(anyLong());
        }

        @DisplayName("좋아요가 없으면, 멱등하게 저장·카운터 감소를 하지 않는다.")
        @Test
        void given_noLike_when_unlike_then_idempotentNoOp() {
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(productService, never()).decreaseLikesCount(anyLong());
        }

        @DisplayName("이미 취소된 좋아요면, 멱등하게 저장·카운터 감소를 하지 않는다.")
        @Test
        void given_alreadyCanceled_when_unlike_then_idempotentNoOp() {
            LikeModel canceled = new LikeModel(USER_ID, PRODUCT_ID);
            canceled.delete();
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(canceled));

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(productService, never()).decreaseLikesCount(anyLong());
        }
    }
}
