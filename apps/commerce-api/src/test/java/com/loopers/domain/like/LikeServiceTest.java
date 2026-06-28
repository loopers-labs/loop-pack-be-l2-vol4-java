package com.loopers.domain.like;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class LikeServiceTest {

    private LikeService likeService;
    private FakeLikeRepository likeRepository;

    @BeforeEach
    void setUp() {
        likeRepository = new FakeLikeRepository();
        likeService = new LikeService(likeRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class AddLikeTest {

        @DisplayName("처음 좋아요를 누르면 true를 반환하고 저장된다.")
        @Test
        void returnsTrue_andSavesLike_whenFirstLike() {
            boolean result = likeService.addLike(1L, 1L);

            assertAll(
                () -> assertThat(result).isTrue(),
                () -> assertThat(likeRepository.existsBy(1L, 1L)).isTrue()
            );
        }

        @DisplayName("이미 좋아요한 상품이면 false를 반환하고 중복 저장되지 않는다.")
        @Test
        void returnsFalse_whenAlreadyLiked() {
            likeService.addLike(1L, 1L);
            boolean result = likeService.addLike(1L, 1L);

            assertAll(
                () -> assertThat(result).isFalse(),
                () -> assertThat(likeRepository.countBy(1L, 1L)).isEqualTo(1)
            );
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class RemoveLikeTest {

        @DisplayName("좋아요가 있으면 true를 반환하고 삭제된다.")
        @Test
        void returnsTrue_andDeletesLike_whenLikeExists() {
            likeService.addLike(1L, 1L);
            boolean result = likeService.removeLike(1L, 1L);

            assertAll(
                () -> assertThat(result).isTrue(),
                () -> assertThat(likeRepository.existsBy(1L, 1L)).isFalse()
            );
        }

        @DisplayName("좋아요가 없으면 false를 반환하고 예외가 발생하지 않는다.")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            boolean result = likeService.removeLike(1L, 1L);
            assertThat(result).isFalse();
        }
    }

    @DisplayName("상품 ID 목록으로 좋아요를 일괄 삭제할 때,")
    @Nested
    class BulkDeleteTest {

        @DisplayName("해당 상품들의 모든 좋아요가 삭제된다.")
        @Test
        void deletesAllLikes_forGivenProductIds() {
            likeService.addLike(1L, 10L);
            likeService.addLike(2L, 10L);
            likeService.addLike(1L, 20L);

            likeService.bulkDeleteByProductIds(List.of(10L));

            assertAll(
                () -> assertThat(likeRepository.existsBy(1L, 10L)).isFalse(),
                () -> assertThat(likeRepository.existsBy(2L, 10L)).isFalse(),
                () -> assertThat(likeRepository.existsBy(1L, 20L)).isTrue()
            );
        }
    }

    static class FakeLikeRepository implements LikeRepository {
        private final List<Like> store = new ArrayList<>();

        @Override
        public boolean existsBy(Long userId, Long productId) {
            return store.stream().anyMatch(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
        }

        public long countBy(Long userId, Long productId) {
            return store.stream().filter(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId)).count();
        }

        @Override
        public void save(Like like) { store.add(like); }

        @Override
        public void deleteBy(Long userId, Long productId) {
            store.removeIf(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
        }

        @Override
        public void deleteAllByProductIdIn(List<Long> productIds) {
            store.removeIf(l -> productIds.contains(l.getProductId()));
        }

        @Override
        public List<Like> findByUserId(Long userId) {
            return store.stream().filter(l -> l.getUserId().equals(userId)).toList();
        }
    }
}
