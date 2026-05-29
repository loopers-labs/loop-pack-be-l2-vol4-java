package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeServiceTest {

    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeService(new FakeLikeRepository());
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class AddLike {

        @DisplayName("처음 좋아요하면 정상 등록된다.")
        @Test
        void adds_whenFirstLike() {
            Like result = likeService.addLike(1L, 10L);
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getProductId()).isEqualTo(10L);
        }

        @DisplayName("이미 좋아요한 상품이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenDuplicate() {
            likeService.addLike(1L, 10L);

            CoreException result = assertThrows(CoreException.class,
                () -> likeService.addLike(1L, 10L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("다른 유저는 같은 상품에 좋아요할 수 있다.")
        @Test
        void adds_whenDifferentUser() {
            likeService.addLike(1L, 10L);
            Like result = likeService.addLike(2L, 10L);
            assertThat(result.getUserId()).isEqualTo(2L);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class RemoveLike {

        @DisplayName("좋아요한 상품이면 정상 취소된다.")
        @Test
        void removes_whenLiked() {
            likeService.addLike(1L, 10L);
            likeService.removeLike(1L, 10L);

            assertThat(likeService.isLiked(1L, 10L)).isFalse();
        }

        @DisplayName("좋아요하지 않은 상품 취소 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotLiked() {
            CoreException result = assertThrows(CoreException.class,
                () -> likeService.removeLike(1L, 10L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("유저의 좋아요 목록 조회 시,")
    @Nested
    class GetLikedProductIds {

        @DisplayName("해당 유저가 좋아요한 상품 ID 목록을 반환한다.")
        @Test
        void returns_likedProductIds() {
            likeService.addLike(1L, 10L);
            likeService.addLike(1L, 20L);
            likeService.addLike(2L, 10L);

            List<Long> result = likeService.getLikedProductIds(1L);
            assertThat(result).containsExactlyInAnyOrder(10L, 20L);
        }

        @DisplayName("좋아요한 상품이 없으면 빈 목록을 반환한다.")
        @Test
        void returns_empty_whenNoLikes() {
            assertThat(likeService.getLikedProductIds(1L)).isEmpty();
        }
    }

    static class FakeLikeRepository implements LikeRepository {
        private final Map<Long, Like> store = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public Like save(Like like) {
            if (like.getId() == 0L) {
                ReflectionTestUtils.setField(like, "id", idSequence.getAndIncrement());
            }
            store.put(like.getId(), like);
            return like;
        }

        @Override
        public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
            return store.values().stream()
                .filter(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId))
                .findFirst();
        }

        @Override
        public List<Like> findAllByUserId(Long userId) {
            return store.values().stream()
                .filter(l -> l.getUserId().equals(userId))
                .toList();
        }

        @Override
        public void delete(Long id) {
            store.remove(id);
        }

        @Override
        public boolean existsByUserIdAndProductId(Long userId, Long productId) {
            return store.values().stream()
                .anyMatch(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
        }
    }
}
