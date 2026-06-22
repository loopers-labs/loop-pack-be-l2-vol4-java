package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeSort;
import com.loopers.domain.product.ProductFilter;
import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductLikeViewRepository;
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
    private FakeProductLikeViewRepository fakeProductLikeViewRepository;

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        fakeProductRepository = new FakeProductRepository();
        fakeProductLikeViewRepository = new FakeProductLikeViewRepository();
        likeService = new LikeService(fakeLikeRepository, fakeProductRepository, fakeProductLikeViewRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("좋아요 등록 시, Like가 저장되고 likeCount가 1 증가한다.")
        @Test
        void like_savesLike_andIncrementsLikeCount() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            fakeProductLikeViewRepository.save(new ProductLikeViewModel(product.getId()));
            Long memberId = 1L;

            // act
            likeService.like(memberId, product.getId());

            // assert
            assertThat(fakeLikeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isTrue();
            assertThat(fakeProductLikeViewRepository.findByProductId(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
        }

        @DisplayName("이미 좋아요한 상품에 재등록 시, 멱등 처리되어 likeCount가 변하지 않는다.")
        @Test
        void like_returnsOk_whenAlreadyLiked() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            fakeProductLikeViewRepository.save(new ProductLikeViewModel(product.getId()));
            Long memberId = 1L;
            likeService.like(memberId, product.getId());

            // act
            likeService.like(memberId, product.getId());

            // assert
            assertThat(fakeLikeRepository.countByMemberIdAndProductId(memberId, product.getId())).isEqualTo(1);
            assertThat(fakeProductLikeViewRepository.findByProductId(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
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
            fakeProductLikeViewRepository.save(new ProductLikeViewModel(product.getId()));
            Long memberId = 1L;
            likeService.like(memberId, product.getId());

            // act
            likeService.unlike(memberId, product.getId());

            // assert
            assertThat(fakeLikeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isFalse();
            assertThat(fakeProductLikeViewRepository.findByProductId(product.getId()).orElseThrow().getLikeCount()).isZero();
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void unlike_throwsException_whenNotLiked() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            fakeProductLikeViewRepository.save(new ProductLikeViewModel(product.getId()));
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
            fakeProductLikeViewRepository.save(new ProductLikeViewModel(product1.getId()));
            fakeProductLikeViewRepository.save(new ProductLikeViewModel(product2.getId()));
            Long memberId = 1L;
            likeService.like(memberId, product1.getId());
            likeService.like(memberId, product2.getId());

            // act
            List<LikeInfo> infos = likeService.getLikeInfosByMemberId(memberId, LikeSort.LATEST);

            // assert
            assertThat(infos).hasSize(2);
        }

        @DisplayName("likes_desc 정렬로 조회하면, likeCount 내림차순으로 반환된다.")
        @Test
        void getLikes_returnsSortedByLikesDesc() {
            // arrange
            ProductModel product1 = fakeProductRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            ProductModel product2 = fakeProductRepository.save(new ProductModel("에어맥스90", 159000L, 1L));
            ProductLikeViewModel plv1 = fakeProductLikeViewRepository.save(new ProductLikeViewModel(product1.getId()));
            ProductLikeViewModel plv2 = fakeProductLikeViewRepository.save(new ProductLikeViewModel(product2.getId()));
            plv2.increment();
            plv2.increment();
            Long memberId = 1L;
            likeService.like(memberId, product1.getId());
            likeService.like(memberId, product2.getId());

            // act
            List<LikeInfo> infos = likeService.getLikeInfosByMemberId(memberId, LikeSort.LIKES_DESC);

            // assert
            assertThat(infos.get(0).productId()).isEqualTo(product2.getId());
            assertThat(infos.get(1).productId()).isEqualTo(product1.getId());
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
        public List<LikeModel> findAllByMemberIdOrderByCreatedAtDesc(Long memberId) {
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
        public Page<ProductModel> findAll(ProductFilter filter, ProductSort sort, PageRequest pageRequest) {
            List<ProductModel> list = new ArrayList<>(store.values());
            return new org.springframework.data.domain.PageImpl<>(list, pageRequest, list.size());
        }

        @Override
        public List<ProductModel> findAllByIds(List<Long> ids) {
            return ids.stream()
                .map(store::get)
                .filter(p -> p != null && p.getDeletedAt() == null)
                .toList();
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

    private static class FakeProductLikeViewRepository implements ProductLikeViewRepository {

        private final Map<Long, ProductLikeViewModel> store = new HashMap<>();

        @Override
        public ProductLikeViewModel save(ProductLikeViewModel view) {
            store.put(view.getProductId(), view);
            return view;
        }

        @Override
        public Optional<ProductLikeViewModel> findByProductId(Long productId) {
            return Optional.ofNullable(store.get(productId));
        }

        @Override
        public Optional<ProductLikeViewModel> findByProductIdForUpdate(Long productId) {
            return findByProductId(productId);
        }

        @Override
        public List<ProductLikeViewModel> findAllByProductIdIn(List<Long> productIds) {
            return productIds.stream()
                .map(store::get)
                .filter(v -> v != null)
                .toList();
        }

        @Override
        public void deleteByProductId(Long productId) {
            store.remove(productId);
        }
    }
}
