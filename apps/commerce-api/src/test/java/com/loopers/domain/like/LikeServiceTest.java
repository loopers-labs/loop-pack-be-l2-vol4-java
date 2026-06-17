package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class LikeServiceTest {

    private LikeService likeService;
    private FakeLikeRepository likeRepository;
    private FakeProductRepository productRepository;

    @BeforeEach
    void setUp() {
        likeRepository = new FakeLikeRepository();
        productRepository = new FakeProductRepository();
        likeService = new LikeService(likeRepository, productRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class LikeTest {

        @DisplayName("처음 좋아요를 누르면 등록되고 상품 좋아요 수가 1 증가한다.")
        @Test
        void registersLike_andIncrementsProductLikeCount() {
            ProductModel product = new ProductModel("상품", "설명", 1000L, 10, 1L);
            productRepository.addProduct(1L, product);

            likeService.like(1L, 1L);

            assertAll(
                () -> assertThat(likeRepository.existsBy(1L, 1L)).isTrue(),
                () -> assertThat(productRepository.findById(1L).get().getLikeCount()).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요한 상품이면 중복 등록되지 않는다.")
        @Test
        void doesNotDuplicateLike_whenAlreadyLiked() {
            ProductModel product = new ProductModel("상품", "설명", 1000L, 10, 1L);
            productRepository.addProduct(1L, product);

            likeService.like(1L, 1L);
            likeService.like(1L, 1L);

            assertThat(productRepository.findById(1L).get().getLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class UnlikeTest {

        @DisplayName("좋아요가 있으면 취소되고 상품 좋아요 수가 1 감소한다.")
        @Test
        void cancelsLike_andDecrementsProductLikeCount() {
            ProductModel product = new ProductModel("상품", "설명", 1000L, 10, 1L);
            productRepository.addProduct(1L, product);

            likeService.like(1L, 1L);
            likeService.unlike(1L, 1L);

            assertAll(
                () -> assertThat(likeRepository.existsBy(1L, 1L)).isFalse(),
                () -> assertThat(productRepository.findById(1L).get().getLikeCount()).isEqualTo(0)
            );
        }

        @DisplayName("좋아요가 없는 상품을 취소해도 예외가 발생하지 않는다.")
        @Test
        void doesNothing_whenLikeDoesNotExist() {
            ProductModel product = new ProductModel("상품", "설명", 1000L, 10, 1L);
            productRepository.addProduct(1L, product);

            assertDoesNotThrow(() -> likeService.unlike(1L, 1L));
        }
    }

    static class FakeLikeRepository implements LikeRepository {
        private final List<Like> store = new ArrayList<>();

        @Override
        public boolean existsBy(Long userId, Long productId) {
            return store.stream().anyMatch(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
        }

        @Override
        public void save(Like like) { store.add(like); }

        @Override
        public void deleteBy(Long userId, Long productId) {
            store.removeIf(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
        }

        @Override
        public List<Like> findByUserId(Long userId) {
            return store.stream().filter(l -> l.getUserId().equals(userId)).toList();
        }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, ProductModel> store = new HashMap<>();

        public void addProduct(Long id, ProductModel product) { store.put(id, product); }

        @Override
        public ProductModel save(ProductModel product) { return product; }

        @Override
        public Optional<ProductModel> findById(Long id) { return Optional.ofNullable(store.get(id)); }

        @Override
        public Page<ProductModel> findAll(Long brandId, ProductSortType sort, Pageable pageable) {
            return new PageImpl<>(new ArrayList<>(store.values()));
        }

        @Override
        public void delete(Long id) { store.remove(id); }

        @Override
        public boolean existsById(Long id) { return store.containsKey(id); }
    }
}
