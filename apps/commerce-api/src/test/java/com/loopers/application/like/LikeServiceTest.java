package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeServiceTest {

    private LikeService likeService;
    private FakeLikeRepository fakeLikeRepository;
    private FakeProductRepository fakeProductRepository;

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        fakeProductRepository = new FakeProductRepository();
        likeService = new LikeService(fakeLikeRepository, fakeProductRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("좋아요 등록 시, Like가 저장되고 likeCount가 1 증가한다.")
        @Test
        void like_savesLike_andIncrementsLikeCount() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            Long memberId = 1L;

            // act
            likeService.like(memberId, product.getId());

            // assert
            assertThat(fakeLikeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isTrue();
            assertThat(fakeProductRepository.findById(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
        }

        @DisplayName("이미 좋아요한 상품에 재등록 시, 멱등 처리되어 likeCount가 변하지 않는다.")
        @Test
        void like_returnsOk_whenAlreadyLiked() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            Long memberId = 1L;
            likeService.like(memberId, product.getId());

            // act
            likeService.like(memberId, product.getId());

            // assert
            assertThat(fakeLikeRepository.countByMemberIdAndProductId(memberId, product.getId())).isEqualTo(1);
            assertThat(fakeProductRepository.findById(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요 취소 시, Like가 삭제되고 likeCount가 1 감소한다.")
        @Test
        void unlike_deletesLike_andDecrementsLikeCount() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            Long memberId = 1L;
            likeService.like(memberId, product.getId());

            // act
            likeService.unlike(memberId, product.getId());

            // assert
            assertThat(fakeLikeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isFalse();
            assertThat(fakeProductRepository.findById(product.getId()).orElseThrow().getLikeCount()).isZero();
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void unlike_throwsException_whenNotLiked() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            Long memberId = 1L;

            // act & assert
            assertThatThrownBy(() -> likeService.unlike(memberId, product.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 좋아요 목록을 조회할 때,")
    @Nested
    class GetLikes {

        @DisplayName("memberId로 조회하면, 해당 회원의 좋아요 목록이 반환된다.")
        @Test
        void getLikes_returnsLikeList_withProductInfo() {
            // arrange
            ProductModel product1 = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            ProductModel product2 = fakeProductRepository.save(new ProductModel("에어맥스90", 159000L, 1L));
            Long memberId = 1L;
            likeService.like(memberId, product1.getId());
            likeService.like(memberId, product2.getId());

            // act
            List<LikeModel> likes = likeService.getLikesByMemberId(memberId);

            // assert
            assertThat(likes).hasSize(2);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Fake Repositories
    // ─────────────────────────────────────────────────────────

    private static class FakeLikeRepository implements LikeRepository {

        private final Map<Long, LikeModel> store = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong(1L);

        @Override
        public LikeModel save(LikeModel like) {
            setId(like, sequence.getAndIncrement());
            store.put(like.getId(), like);
            return like;
        }

        @Override
        public Optional<LikeModel> findByMemberIdAndProductId(Long memberId, Long productId) {
            return store.values().stream()
                .filter(l -> l.getMemberId().equals(memberId) && l.getProductId().equals(productId))
                .findFirst();
        }

        @Override
        public List<LikeModel> findAllByMemberId(Long memberId) {
            return store.values().stream()
                .filter(l -> l.getMemberId().equals(memberId))
                .toList();
        }

        @Override
        public void delete(LikeModel like) {
            store.remove(like.getId());
        }

        @Override
        public void deleteAllByProductId(Long productId) {
            store.values().removeIf(l -> l.getProductId().equals(productId));
        }

        public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
            return store.values().stream()
                .anyMatch(l -> l.getMemberId().equals(memberId) && l.getProductId().equals(productId));
        }

        public long countByMemberIdAndProductId(Long memberId, Long productId) {
            return store.values().stream()
                .filter(l -> l.getMemberId().equals(memberId) && l.getProductId().equals(productId))
                .count();
        }

        private void setId(LikeModel like, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(like, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class FakeProductRepository implements ProductRepository {

        private final Map<Long, ProductModel> store = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong(1L);

        @Override
        public ProductModel save(ProductModel product) {
            setId(product, sequence.getAndIncrement());
            store.put(product.getId(), product);
            return product;
        }

        @Override
        public Optional<ProductModel> findById(Long id) {
            return Optional.ofNullable(store.get(id))
                .filter(p -> p.getDeletedAt() == null);
        }

        @Override
        public Optional<ProductModel> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public List<ProductModel> findAllByBrandId(Long brandId) {
            return store.values().stream().filter(p -> p.getBrandId().equals(brandId)).toList();
        }

        @Override
        public Page<ProductModel> findAll(Long brandId, ProductSort sort, PageRequest pageRequest) {
            List<ProductModel> list = new ArrayList<>(store.values());
            return new org.springframework.data.domain.PageImpl<>(list, pageRequest, list.size());
        }

        private void setId(ProductModel product, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(product, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
