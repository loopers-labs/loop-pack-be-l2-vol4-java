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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 100L;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeService likeService;

    @DisplayName("좋아요 등록 시")
    @Nested
    class Like {

        @DisplayName("좋아요가 없으면 저장하고 true를 반환한다")
        @Test
        void savesAndReturnsTrue_whenNotLikedYet() {
            // given
            when(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(false);

            // when
            boolean created = likeService.like(USER_ID, PRODUCT_ID);

            // then
            assertThat(created).isTrue();
            verify(likeRepository, times(1)).save(any(LikeModel.class));
        }

        @DisplayName("이미 좋아요한 상태면 멱등으로 저장하지 않고 false를 반환한다")
        @Test
        void doesNothingAndReturnsFalse_whenAlreadyLiked() {
            // given
            when(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(true);

            // when
            boolean created = likeService.like(USER_ID, PRODUCT_ID);

            // then
            assertThat(created).isFalse();
            verify(likeRepository, never()).save(any(LikeModel.class));
        }
    }

    @DisplayName("좋아요 취소 시")
    @Nested
    class Unlike {

        @DisplayName("실제로 삭제되면 true를 반환한다")
        @Test
        void returnsTrue_whenActuallyDeleted() {
            // given
            when(likeRepository.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(1);

            // when
            boolean deleted = likeService.unlike(USER_ID, PRODUCT_ID);

            // then
            assertThat(deleted).isTrue();
        }

        @DisplayName("삭제할 좋아요가 없으면 멱등으로 false를 반환한다")
        @Test
        void returnsFalse_whenNothingToDelete() {
            // given
            when(likeRepository.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(0);

            // when
            boolean deleted = likeService.unlike(USER_ID, PRODUCT_ID);

            // then
            assertThat(deleted).isFalse();
        }
    }
}
