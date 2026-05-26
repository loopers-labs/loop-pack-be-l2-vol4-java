package com.loopers.domain.like;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LikeServiceUnitTest {

    private LikeService likeService;
    private FakeProductLikeRepository productLikeRepository;

    @BeforeEach
    void setUp() {
        productLikeRepository = new FakeProductLikeRepository();
        likeService = new LikeService(productLikeRepository);
    }

    static class FakeProductLikeRepository implements ProductLikeRepository {
        private final Map<String, ProductLike> store = new HashMap<>();

        private String key(Long userId, Long productId) {
            return userId + ":" + productId;
        }

        @Override
        public ProductLike save(ProductLike productLike) {
            store.put(key(productLike.getUserId(), productLike.getProductId()), productLike);
            return productLike;
        }

        @Override
        public boolean existsByUserIdAndProductId(Long userId, Long productId) {
            return store.containsKey(key(userId, productId));
        }

        @Override
        public void deleteByUserIdAndProductId(Long userId, Long productId) {
            store.remove(key(userId, productId));
        }

        @Override
        public List<ProductLike> findAllByUserId(Long userId) {
            return store.values().stream()
                .filter(l -> l.getUserId().equals(userId))
                .toList();
        }
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("좋아요하지 않은 상품에 좋아요하면, true를 반환하고 좋아요가 저장된다.")
        @Test
        void returnsTrue_whenLikeIsAdded() {
            boolean result = likeService.like(1L, 10L);

            assertThat(result).isTrue();
            assertThat(productLikeRepository.existsByUserIdAndProductId(1L, 10L)).isTrue();
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면, false를 반환하고 중복 저장되지 않는다.")
        @Test
        void returnsFalse_whenAlreadyLiked() {
            likeService.like(1L, 10L);

            boolean result = likeService.like(1L, 10L);

            assertThat(result).isFalse();
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상품을 취소하면, true를 반환하고 좋아요가 삭제된다.")
        @Test
        void returnsTrue_whenLikeIsRemoved() {
            likeService.like(1L, 10L);

            boolean result = likeService.unlike(1L, 10L);

            assertThat(result).isTrue();
            assertThat(productLikeRepository.existsByUserIdAndProductId(1L, 10L)).isFalse();
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면, false를 반환하고 아무 변화가 없다.")
        @Test
        void returnsFalse_whenNotLiked() {
            boolean result = likeService.unlike(1L, 10L);

            assertThat(result).isFalse();
        }
    }

    @DisplayName("좋아요 목록을 조회할 때,")
    @Nested
    class GetLikes {

        @DisplayName("유저가 좋아요한 상품 목록을 반환한다.")
        @Test
        void returnsLikedProducts_byUserId() {
            likeService.like(1L, 10L);
            likeService.like(1L, 20L);
            likeService.like(2L, 10L);

            List<ProductLike> result = likeService.getLikesByUserId(1L);

            assertThat(result).hasSize(2);
        }
    }
}
