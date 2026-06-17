package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceUnitTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private LikeService likeService;

    @DisplayName("좋아요 등록할 때")
    @Nested
    class Like {

        @DisplayName("복합키에 해당하는 데이터가 없으면, 새 LikeModel을 저장한다.")
        @Test
        void savesNewModel_whenNoRow() {
            // given
            given(likeRepository.find(any(LikeId.class))).willReturn(Optional.empty());

            // when
            likeService.like(1L, 2L);

            // then
            verify(likeRepository).save(any(LikeModel.class));
            verify(productService).increaseLikeCount(2L);
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 likedAt이 null이면, 모델의 like()를 호출해 좋아요 상태로 만든다.")
        @Test
        void invokesLike_whenRowExistsButCancelled() {
            // given
            LikeModel model = LikeModel.of(1L, 2L);
            model.unlike();
            given(likeRepository.find(any(LikeId.class))).willReturn(Optional.of(model));

            // when
            likeService.like(1L, 2L);

            // then
            assertThat(model.isLiked()).isTrue();
            verify(likeRepository, never()).save(any());
            verify(productService).increaseLikeCount(2L);
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 이미 좋아요 상태면, likedAt 시각을 유지한다.")
        @Test
        void keepsLikedAt_whenAlreadyLiked() {
            // given
            LikeModel model = LikeModel.of(1L, 2L);
            ZonedDateTime before = model.getLikedAt();
            given(likeRepository.find(any(LikeId.class))).willReturn(Optional.of(model));

            // when
            likeService.like(1L, 2L);

            // then
            assertThat(model.getLikedAt()).isEqualTo(before);
            verify(likeRepository, never()).save(any());
            verify(productService, never()).increaseLikeCount(anyLong());
        }
    }

    @DisplayName("좋아요 취소할 때")
    @Nested
    class Unlike {

        @DisplayName("복합키에 해당하는 데이터가 없으면, 아무것도 하지 않는다.")
        @Test
        void doesNothing_whenNoRow() {
            // given
            given(likeRepository.find(any(LikeId.class))).willReturn(Optional.empty());

            // when
            likeService.unlike(1L, 2L);

            // then
            verify(likeRepository, never()).save(any());
            verify(productService, never()).decreaseLikeCount(anyLong());
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 좋아요 상태면, likedAt을 null로 만든다.")
        @Test
        void clearsLikedAt_whenLiked() {
            // given
            LikeModel model = LikeModel.of(1L, 2L);
            given(likeRepository.find(any(LikeId.class))).willReturn(Optional.of(model));

            // when
            likeService.unlike(1L, 2L);

            // then
            assertThat(model.isLiked()).isFalse();
            verify(likeRepository, never()).save(any());
            verify(productService).decreaseLikeCount(2L);
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 이미 취소 상태면, 취소 상태를 유지한다.")
        @Test
        void keepsCancelled_whenAlreadyCancelled() {
            // given
            LikeModel model = LikeModel.of(1L, 2L);
            model.unlike();
            given(likeRepository.find(any(LikeId.class))).willReturn(Optional.of(model));

            // when
            likeService.unlike(1L, 2L);

            // then
            assertThat(model.isLiked()).isFalse();
            verify(productService, never()).decreaseLikeCount(anyLong());
        }
    }
}