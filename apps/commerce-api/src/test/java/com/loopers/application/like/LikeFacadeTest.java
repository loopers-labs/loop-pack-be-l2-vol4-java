package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @InjectMocks
    private LikeFacade likeFacade;

    @Mock
    private LikeService likeService;

    @Mock
    private ProductService productService;

    @Test
    @DisplayName("좋아요를 처음 등록하면 이력이 추가되고 상품의 좋아요 수가 증가한다.")
    void addLike_NewLike_ShouldAddRecordAndIncreaseCount() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        
        // when
        likeFacade.addLike(userId, productId);

        // then
        verify(likeService).addLikeRecord(userId, productId);
        verify(productService).increaseLikeCount(productId);
    }

    @Test
    @DisplayName("이미 좋아요를 누른 상품에 다시 좋아요를 요청하면 추가 로직 없이 성공한다.")
    void addLike_DuplicateLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        
        doThrow(new RuntimeException("Duplicate Key"))
                .when(likeService).addLikeRecord(userId, productId);

        // when
        likeFacade.addLike(userId, productId);

        // then
        verify(likeService).addLikeRecord(userId, productId);
        verify(productService, never()).increaseLikeCount(productId);
    }

    @Test
    @DisplayName("좋아요를 취소하면 이력이 삭제되고 상품의 좋아요 수가 감소한다.")
    void removeLike_ExistingLike_ShouldRemoveRecordAndDecreaseCount() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeService.removeLikeRecord(userId, productId)).willReturn(true);

        // when
        likeFacade.removeLike(userId, productId);

        // then
        verify(likeService).removeLikeRecord(userId, productId);
        verify(productService).decreaseLikeCount(productId);
    }

    @Test
    @DisplayName("좋아요를 누른 적 없는 상품에 취소를 요청하면 추가 로직 없이 성공한다.")
    void removeLike_NonExistentLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeService.removeLikeRecord(userId, productId)).willReturn(false);

        // when
        likeFacade.removeLike(userId, productId);

        // then
        verify(likeService).removeLikeRecord(userId, productId);
        verify(productService, never()).decreaseLikeCount(productId);
    }
}
