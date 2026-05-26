package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeService likeService;

    @DisplayName("좋아요 등록 시, ")
    @Nested
    class Like {

        @DisplayName("Like 가 신규로 저장되면 true 를 반환한다.")
        @Test
        void returnsTrue_whenLikeIsNewlySaved() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.saveIfAbsent(any(LikeModel.class))).willReturn(true);

            // when
            boolean inserted = likeService.like(userId, productId);

            // then
            assertThat(inserted).isTrue();
            verify(likeRepository).saveIfAbsent(any(LikeModel.class));
        }

        @DisplayName("이미 같은 Like 가 존재해 저장되지 않으면 false 를 반환한다 (멱등).")
        @Test
        void returnsFalse_whenLikeAlreadyExists() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.saveIfAbsent(any(LikeModel.class))).willReturn(false);

            // when
            boolean inserted = likeService.like(userId, productId);

            // then
            assertThat(inserted).isFalse();
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class Unlike {

        @DisplayName("Like 가 실제로 삭제되면 true 를 반환한다.")
        @Test
        void returnsTrue_whenLikeIsDeleted() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.deleteByUserIdAndProductId(userId, productId)).willReturn(1);

            // when
            boolean deleted = likeService.unlike(userId, productId);

            // then
            assertThat(deleted).isTrue();
            verify(likeRepository).deleteByUserIdAndProductId(userId, productId);
        }

        @DisplayName("삭제할 Like 가 존재하지 않으면 false 를 반환한다 (멱등).")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.deleteByUserIdAndProductId(userId, productId)).willReturn(0);

            // when
            boolean deleted = likeService.unlike(userId, productId);

            // then
            assertThat(deleted).isFalse();
        }
    }
}
