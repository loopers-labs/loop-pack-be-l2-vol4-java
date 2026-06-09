package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeService likeService;

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("활성 좋아요가 없으면 새 좋아요를 저장한다.")
        @Test
        void saves_new_like_when_no_active_like_exists() {
            when(likeRepository.findActiveLike(1L, 1L)).thenReturn(Optional.empty());
            when(likeRepository.save(any(LikeModel.class))).thenReturn(new LikeModel(1L, 1L));

            likeService.like(1L, 1L);

            verify(likeRepository).save(any(LikeModel.class));
        }

        @DisplayName("이미 활성 좋아요가 있으면 저장을 생략한다. (멱등)")
        @Test
        void skips_save_when_active_like_already_exists() {
            when(likeRepository.findActiveLike(1L, 1L)).thenReturn(Optional.of(new LikeModel(1L, 1L)));

            likeService.like(1L, 1L);

            verify(likeRepository, never()).save(any());
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("활성 좋아요가 있으면 soft delete 처리된다.")
        @Test
        void soft_deletes_like_when_active_like_exists() {
            LikeModel like = new LikeModel(1L, 1L);
            when(likeRepository.findActiveLike(1L, 1L)).thenReturn(Optional.of(like));

            likeService.unlike(1L, 1L);

            assertThat(like.getDeletedAt()).isNotNull();
        }

        @DisplayName("활성 좋아요가 없으면 아무 동작도 하지 않는다. (멱등)")
        @Test
        void does_nothing_when_no_active_like_exists() {
            when(likeRepository.findActiveLike(1L, 1L)).thenReturn(Optional.empty());

            likeService.unlike(1L, 1L);

            verify(likeRepository, never()).save(any());
        }
    }

    @DisplayName("회원의 좋아요 목록을 조회할 때,")
    @Nested
    class GetLikedByMember {

        @DisplayName("해당 회원의 활성 좋아요 목록을 반환한다.")
        @Test
        void returns_active_likes_for_member() {
            List<LikeModel> likes = List.of(new LikeModel(1L, 1L), new LikeModel(1L, 2L));
            when(likeRepository.findAllActiveByMemberId(1L)).thenReturn(likes);

            List<LikeModel> result = likeService.getLikedByMember(1L);

            assertThat(result).hasSize(2);
        }
    }

    @DisplayName("상품의 좋아요 수를 조회할 때,")
    @Nested
    class CountLikes {

        @DisplayName("해당 상품의 활성 좋아요 수를 반환한다.")
        @Test
        void returns_active_like_count_for_product() {
            when(likeRepository.countActiveByProductId(1L)).thenReturn(42L);

            long count = likeService.countLikes(1L);

            assertThat(count).isEqualTo(42L);
        }
    }
}