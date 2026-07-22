package com.loopers.application.catalog.ranking;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.like.ProductLike;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.ranking.ProductRankMvRepository;
import com.loopers.domain.catalog.ranking.RankingPeriod;
import com.loopers.domain.catalog.ranking.RankingRepository;
import com.loopers.support.domain.DomainEntity;
import com.loopers.support.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingQueryServiceTest {

    @DisplayName("Redis 랭킹 순서대로 상품 정보를 조합하고 사용자 좋아요 여부를 batch 조회한다.")
    @Test
    void returnsRankingProductsWithLikedFlags() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 12);
        FakeRankingRepository rankingRepository = new FakeRankingRepository();
        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Brand> brands = new HashMap<>();
        FakeProductLikeRepository productLikeRepository = new FakeProductLikeRepository();

        brands.put(1L, withId(new Brand("Loopers", "테스트 브랜드"), 1L));
        products.put(1L, withId(new Product(1L, "상품1", "설명1", 1_000L, 10), 1L));
        products.put(2L, withId(new Product(1L, "상품2", "설명2", 2_000L, 10), 2L));
        productLikeRepository.save(new ProductLike("user1", 1L));
        rankingRepository.entries.put(date, List.of(
            new RankingRepository.Entry(2L, 1L, 3.0),
            new RankingRepository.Entry(1L, 2L, 2.0)
        ));

        RankingQueryService service = new RankingQueryService(
            rankingRepository,
            new FakeProductRankMvRepository(),
            new FakeProductRepository(products),
            new FakeBrandRepository(brands),
            productLikeRepository
        );

        // act
        PageResult<RankingResult> result = service.getRankings(new RankingQuery.Search(date, 0, 20, "user1"));

        // assert
        assertAll(
            () -> assertThat(result.totalElements()).isEqualTo(2L),
            () -> assertThat(result.items()).extracting(RankingResult::rank).containsExactly(1L, 2L),
            () -> assertThat(result.items()).extracting(RankingResult::score).containsExactly(3.0, 2.0),
            () -> assertThat(result.items()).extracting(item -> item.product().id()).containsExactly(2L, 1L),
            () -> assertThat(result.items()).extracting(item -> item.product().liked()).containsExactly(false, true),
            () -> assertThat(productLikeRepository.findLikedProductIdsCallCount).isEqualTo(1)
        );
    }

    @DisplayName("랭킹에 판매 중지 상품이 섞여 있으면 노출 가능한 상품만 페이지 응답에 포함한다.")
    @Test
    void excludesStoppedProductsFromRankingResponse() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 12);
        FakeRankingRepository rankingRepository = new FakeRankingRepository();
        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Brand> brands = new HashMap<>();

        brands.put(1L, withId(new Brand("Loopers", "테스트 브랜드"), 1L));
        Product stoppedProduct = withId(new Product(1L, "중지상품", "설명", 1_000L, 10), 1L);
        stoppedProduct.stopSelling();
        products.put(1L, stoppedProduct);
        products.put(2L, withId(new Product(1L, "판매상품1", "설명", 2_000L, 10), 2L));
        products.put(3L, withId(new Product(1L, "판매상품2", "설명", 3_000L, 10), 3L));
        rankingRepository.entries.put(date, List.of(
            new RankingRepository.Entry(1L, 1L, 5.0),
            new RankingRepository.Entry(2L, 2L, 3.0),
            new RankingRepository.Entry(3L, 3L, 2.0)
        ));

        RankingQueryService service = new RankingQueryService(
            rankingRepository,
            new FakeProductRankMvRepository(),
            new FakeProductRepository(products),
            new FakeBrandRepository(brands),
            new FakeProductLikeRepository()
        );

        // act
        PageResult<RankingResult> result = service.getRankings(new RankingQuery.Search(date, 0, 2, null));

        // assert
        assertAll(
            () -> assertThat(result.items()).extracting(item -> item.product().id()).containsExactly(2L, 3L),
            () -> assertThat(result.items()).extracting(RankingResult::rank).containsExactly(2L, 3L),
            () -> assertThat(result.totalElements()).isEqualTo(2L),
            () -> assertThat(result.hasNext()).isFalse()
        );
    }

    @DisplayName("음수 page와 0 이하 size는 기본값으로 정규화한다.")
    @Test
    void normalizesInvalidPageAndSize() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 12);
        FakeRankingRepository rankingRepository = new FakeRankingRepository();
        rankingRepository.entries.put(date, List.of());
        RankingQueryService service = new RankingQueryService(
            rankingRepository,
            new FakeProductRankMvRepository(),
            new FakeProductRepository(Map.of()),
            new FakeBrandRepository(Map.of()),
            new FakeProductLikeRepository()
        );

        // act
        PageResult<RankingResult> result = service.getRankings(new RankingQuery.Search(date, -1, 0, null));

        // assert
        assertAll(
            () -> assertThat(result.page()).isZero(),
            () -> assertThat(result.size()).isEqualTo(20)
        );
    }

    @DisplayName("weekly 랭킹 조회는 Redis 일간 랭킹이 아니라 주간 MV 랭킹을 조회한다.")
    @Test
    void returnsWeeklyRankingsFromMaterializedView() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 22);
        LocalDate weekStart = LocalDate.of(2026, 7, 20);
        LocalDate weekEnd = LocalDate.of(2026, 7, 26);
        FakeRankingRepository rankingRepository = new FakeRankingRepository();
        FakeProductRankMvRepository productRankMvRepository = new FakeProductRankMvRepository();
        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Brand> brands = new HashMap<>();

        brands.put(1L, withId(new Brand("Loopers", "테스트 브랜드"), 1L));
        products.put(1L, withId(new Product(1L, "상품1", "설명1", 1_000L, 10), 1L));
        products.put(2L, withId(new Product(1L, "상품2", "설명2", 2_000L, 10), 2L));
        rankingRepository.entries.put(date, List.of(
            new RankingRepository.Entry(1L, 1L, 999.0)
        ));
        productRankMvRepository.entries.put(
            new ProductRankMvKey(RankingPeriod.WEEKLY, weekStart, weekEnd),
            List.of(
                new RankingRepository.Entry(2L, 1L, 92.0),
                new RankingRepository.Entry(1L, 2L, 50.0)
            )
        );

        RankingQueryService service = new RankingQueryService(
            rankingRepository,
            productRankMvRepository,
            new FakeProductRepository(products),
            new FakeBrandRepository(brands),
            new FakeProductLikeRepository()
        );

        // act
        PageResult<RankingResult> result = service.getRankings(
            new RankingQuery.Search(date, RankingPeriod.WEEKLY, 0, 20, null)
        );

        // assert
        assertAll(
            () -> assertThat(result.items()).extracting(item -> item.product().id()).containsExactly(2L, 1L),
            () -> assertThat(result.items()).extracting(RankingResult::score).containsExactly(92.0, 50.0),
            () -> assertThat(rankingRepository.findRankingsCallCount).isZero(),
            () -> assertThat(productRankMvRepository.findRankingsCallCount).isEqualTo(1)
        );
    }

    private static class FakeRankingRepository implements RankingRepository {
        private final Map<LocalDate, List<Entry>> entries = new HashMap<>();
        private int findRankingsCallCount = 0;

        @Override
        public void incrementScore(LocalDate date, Long productId, double score, Duration ttl) {
        }

        @Override
        public List<Entry> findRankings(LocalDate date, int page, int size) {
            findRankingsCallCount++;
            return entries.getOrDefault(date, List.of())
                .stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        }

        @Override
        public Optional<Entry> findRank(LocalDate date, Long productId) {
            return entries.getOrDefault(date, List.of())
                .stream()
                .filter(entry -> entry.productId().equals(productId))
                .findFirst();
        }

        @Override
        public long count(LocalDate date) {
            return entries.getOrDefault(date, List.of()).size();
        }
    }

    private record ProductRankMvKey(
        RankingPeriod period,
        LocalDate periodStartDate,
        LocalDate periodEndDate
    ) {
    }

    private static class FakeProductRankMvRepository implements ProductRankMvRepository {
        private final Map<ProductRankMvKey, List<RankingRepository.Entry>> entries = new HashMap<>();
        private int findRankingsCallCount = 0;

        @Override
        public List<RankingRepository.Entry> findRankings(
            RankingPeriod period,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            int page,
            int size
        ) {
            findRankingsCallCount++;
            return entries.getOrDefault(new ProductRankMvKey(period, periodStartDate, periodEndDate), List.of())
                .stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        }

        @Override
        public long count(RankingPeriod period, LocalDate periodStartDate, LocalDate periodEndDate) {
            return entries.getOrDefault(new ProductRankMvKey(period, periodStartDate, periodEndDate), List.of()).size();
        }
    }

    private record FakeProductRepository(Map<Long, Product> products) implements ProductRepository {
        @Override
        public Product save(Product product) {
            return product;
        }

        @Override
        public Optional<Product> find(Long id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public Optional<Product> findOnSale(Long id) {
            return find(id).filter(Product::isOnSale);
        }

        @Override
        public List<Product> findAllByIds(Collection<Long> ids) {
            return ids.stream().map(products::get).toList();
        }

        @Override
        public List<Product> findByBrandId(Long brandId) {
            return products.values().stream()
                .filter(product -> product.getBrandId().equals(brandId))
                .toList();
        }

        @Override
        public List<Product> search(ProductSearchCondition condition) {
            return List.of();
        }

        @Override
        public long count(ProductSearchCondition condition) {
            return 0;
        }

        @Override
        public int increaseLikeCount(Long productId) {
            return 0;
        }

        @Override
        public int decreaseLikeCount(Long productId) {
            return 0;
        }
    }

    private record FakeBrandRepository(Map<Long, Brand> brands) implements BrandRepository {
        @Override
        public Brand save(Brand brand) {
            return brand;
        }

        @Override
        public Optional<Brand> find(Long id) {
            return Optional.ofNullable(brands.get(id));
        }

        @Override
        public Optional<Brand> findActive(Long id) {
            return find(id).filter(Brand::isActive);
        }

        @Override
        public List<Brand> findAllByIds(Collection<Long> ids) {
            return ids.stream().map(brands::get).toList();
        }

        @Override
        public List<Brand> findAll(int page, int size) {
            return List.of();
        }

        @Override
        public long countAll() {
            return brands.size();
        }
    }

    private record LikeKey(String userId, Long productId) {
    }

    private static class FakeProductLikeRepository implements ProductLikeRepository {
        private final Map<LikeKey, ProductLike> productLikes = new HashMap<>();
        private int findLikedProductIdsCallCount = 0;

        @Override
        public ProductLike save(ProductLike productLike) {
            productLikes.put(new LikeKey(productLike.getUserId(), productLike.getProductId()), productLike);
            return productLike;
        }

        @Override
        public boolean saveIfAbsent(ProductLike productLike) {
            save(productLike);
            return true;
        }

        @Override
        public Optional<ProductLike> find(String userId, Long productId) {
            return Optional.ofNullable(productLikes.get(new LikeKey(userId, productId)));
        }

        @Override
        public boolean exists(String userId, Long productId) {
            return productLikes.containsKey(new LikeKey(userId, productId));
        }

        @Override
        public void delete(ProductLike productLike) {
            productLikes.remove(new LikeKey(productLike.getUserId(), productLike.getProductId()));
        }

        @Override
        public boolean delete(String userId, Long productId) {
            return productLikes.remove(new LikeKey(userId, productId)) != null;
        }

        @Override
        public List<ProductLike> findByUserId(String userId, int page, int size) {
            return List.of();
        }

        @Override
        public Set<Long> findLikedProductIds(String userId, Collection<Long> productIds) {
            findLikedProductIdsCallCount++;
            return productIds.stream()
                .filter(productId -> productLikes.containsKey(new LikeKey(userId, productId)))
                .collect(Collectors.toSet());
        }

        @Override
        public long countByUserId(String userId) {
            return 0;
        }
    }

    private static <T extends DomainEntity> T withId(T entity, Long id) {
        try {
            Field field = DomainEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
