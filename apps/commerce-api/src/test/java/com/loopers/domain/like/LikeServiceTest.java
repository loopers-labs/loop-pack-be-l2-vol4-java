package com.loopers.domain.like;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LikeServiceTest {

    private LikeService likeService;
    private LikeRepository likeRepository;
    private LikeEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        eventPublisher = mock(LikeEventPublisher.class);
        likeService = new LikeService(likeRepository, eventPublisher);
    }

    @DisplayName("좋아요한 상품 ID 목록을 조회할 때,")
    @Nested
    class GetLikedProductIds {

        @DisplayName("좋아요가 있으면 해당 상품 ID 목록을 반환한다.")
        @Test
        void returnsProductIds_whenLikesExist() {
            // given
            Long userId = 1L;
            LikeModel like1 = new LikeModel(userId, 10L);
            LikeModel like2 = new LikeModel(userId, 20L);
            when(likeRepository.findAllByUserId(userId)).thenReturn(List.of(like1, like2));

            // when
            List<Long> result = likeService.getLikedProductIds(userId);

            // then
            assertThat(result).containsExactlyInAnyOrder(10L, 20L);
        }

        @DisplayName("좋아요가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoLikesExist() {
            // given
            Long userId = 1L;
            when(likeRepository.findAllByUserId(userId)).thenReturn(List.of());

            // when
            List<Long> result = likeService.getLikedProductIds(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Register {

        @DisplayName("INSERT 가 실행되면 APPLIED 를 반환한다.")
        @Test
        void returnsApplied_whenInserted() {
            // given
            Long userId = 1L;
            Long productId = 2L;
            when(likeRepository.save(any(LikeModel.class))).thenReturn(true);

            // when
            LikeResult result = likeService.register(userId, productId);

            // then
            assertThat(result).isEqualTo(LikeResult.APPLIED);
        }

        @DisplayName("이미 좋아요가 있어 INSERT 가 무시되면 IGNORED 를 반환한다.")
        @Test
        void returnsIgnored_whenInsertSkipped() {
            // given
            Long userId = 1L;
            Long productId = 2L;
            when(likeRepository.save(any(LikeModel.class))).thenReturn(false);

            // when
            LikeResult result = likeService.register(userId, productId);

            // then
            assertThat(result).isEqualTo(LikeResult.IGNORED);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Cancel {

        @DisplayName("DELETE 가 실행되면 APPLIED 를 반환한다.")
        @Test
        void returnsApplied_whenDeleted() {
            // given
            Long userId = 1L;
            Long productId = 2L;
            when(likeRepository.deleteByUserIdAndProductId(userId, productId)).thenReturn(true);

            // when
            LikeResult result = likeService.cancel(userId, productId);

            // then
            assertThat(result).isEqualTo(LikeResult.APPLIED);
        }

        @DisplayName("좋아요가 없어 DELETE 가 무시되면 예외 없이 IGNORED 를 반환한다.")
        @Test
        void returnsIgnored_whenNothingToDelete() {
            // given
            Long userId = 1L;
            Long productId = 2L;
            when(likeRepository.deleteByUserIdAndProductId(userId, productId)).thenReturn(false);

            // when / then
            LikeResult result = assertDoesNotThrow(() -> likeService.cancel(userId, productId));
            assertThat(result).isEqualTo(LikeResult.IGNORED);
        }
    }
}
