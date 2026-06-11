package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @InjectMocks
    private LikeService likeService;

    @Mock
    private LikeRepository likeRepository;

    @Test
    @DisplayName("좋아요 이력 추가를 요청하면 리포지토리에 저장된다.")
    void addLikeRecord_ShouldSave() {
        // given
        Long userId = 1L;
        Long productId = 10L;

        // when
        likeService.addLikeRecord(userId, productId);

        // then
        verify(likeRepository).save(any(ProductLikeModel.class));
    }

    @Test
    @DisplayName("좋아요 이력 삭제를 요청하면 리포지토리의 삭제 메서드가 호출된다.")
    void removeLikeRecord_ShouldDelete() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeRepository.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.of(new ProductLikeModel(userId, productId)));

        // when
        likeService.removeLikeRecord(userId, productId);

        // then
        verify(likeRepository).deleteByUserIdAndProductId(userId, productId);
    }

    @Test
    @DisplayName("내가 좋아요 한 상품 목록을 조회하면 전체 목록이 반환된다.")
    void getMyLikes_ShouldReturnList() {
        // given
        Long userId = 1L;
        ProductLikeModel like1 = new ProductLikeModel(userId, 10L);
        ProductLikeModel like2 = new ProductLikeModel(userId, 20L);
        given(likeRepository.findAllByUserId(userId)).willReturn(java.util.List.of(like1, like2));

        // when
        java.util.List<ProductLikeModel> result = likeService.getMyLikes(userId);

        // then
        assertThat(result).hasSize(2);
        verify(likeRepository).findAllByUserId(userId);
    }

    @Test
    @DisplayName("좋아요 이력이 존재하면 true를 반환한다.")
    void existsLikeRecord_WhenExists_ShouldReturnTrue() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeRepository.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.of(new ProductLikeModel(userId, productId)));

        // when
        boolean result = likeService.existsLikeRecord(userId, productId);

        // then
        assertThat(result).isTrue();
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
    }

    @Test
    @DisplayName("좋아요 이력이 없으면 false를 반환한다.")
    void existsLikeRecord_WhenNotExists_ShouldReturnFalse() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeRepository.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.empty());

        // when
        boolean result = likeService.existsLikeRecord(userId, productId);

        // then
        assertThat(result).isFalse();
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
    }
}
