package com.loopers.application.catalog.product;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.like.ProductLike;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.product.ProductStatus;
import com.loopers.support.domain.DomainEntity;
import com.loopers.support.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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

class ProductQueryServiceTest {

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class SearchOnSaleProducts {

        @DisplayName("현재 페이지 상품들의 좋아요 여부를 batch 조회로 계산한다.")
        @Test
        void resolvesLikedFlagsWithBatchQuery() {
            // arrange
            Map<Long, Brand> brands = new HashMap<>();
            Map<Long, Product> products = new LinkedHashMap<>();
            FakeProductLikeRepository productLikeRepository = new FakeProductLikeRepository();

            brands.put(1L, withId(new Brand("Loopers", "테스트 브랜드"), 1L));
            products.put(1L, withId(new Product(1L, "상품1", "설명1", 1_000L, 10), 1L));
            products.put(2L, withId(new Product(1L, "상품2", "설명2", 2_000L, 10), 2L));
            productLikeRepository.save(new ProductLike("user1", 2L));

            ProductQueryService service = new ProductQueryService(
                new FakeProductRepository(products),
                new FakeBrandRepository(brands),
                productLikeRepository
            );

            // act
            PageResult<ProductResult> results = service.searchOnSaleProducts(
                new ProductQuery.Search(null, 0, 20, "latest", "user1")
            );

            // assert
            assertAll(
                () -> assertThat(results.items()).hasSize(2),
                () -> assertThat(results.totalElements()).isEqualTo(2),
                () -> assertThat(results.items().get(0).liked()).isFalse(),
                () -> assertThat(results.items().get(1).liked()).isTrue(),
                () -> assertThat(productLikeRepository.findLikedProductIdsCallCount).isEqualTo(1),
                () -> assertThat(productLikeRepository.existsCallCount).isZero()
            );
        }

        @DisplayName("brandId가 있으면 해당 브랜드 상품만 조회한다.")
        @Test
        void filtersByBrandId() {
            // arrange
            Map<Long, Brand> brands = new HashMap<>();
            Map<Long, Product> products = new LinkedHashMap<>();
            FakeProductLikeRepository productLikeRepository = new FakeProductLikeRepository();

            brands.put(1L, withId(new Brand("Loopers", "테스트 브랜드"), 1L));
            brands.put(2L, withId(new Brand("Other", "다른 브랜드"), 2L));
            products.put(1L, withId(new Product(1L, "상품1", "설명1", 1_000L, 10), 1L));
            products.put(2L, withId(new Product(2L, "상품2", "설명2", 2_000L, 10), 2L));

            ProductQueryService service = new ProductQueryService(
                new FakeProductRepository(products),
                new FakeBrandRepository(brands),
                productLikeRepository
            );

            // act
            PageResult<ProductResult> results = service.searchOnSaleProducts(
                new ProductQuery.Search(2L, 0, 20, "latest", null)
            );

            // assert
            assertAll(
                () -> assertThat(results.items()).hasSize(1),
                () -> assertThat(results.items().get(0).brandId()).isEqualTo(2L),
                () -> assertThat(results.totalElements()).isEqualTo(1)
            );
        }
    }

    @DisplayName("ADMIN 상품 목록을 조회할 때, ")
    @Nested
    class SearchProducts {

        @DisplayName("판매 중지 상품도 함께 조회한다.")
        @Test
        void returnsStoppedProducts() {
            // arrange
            Map<Long, Brand> brands = new HashMap<>();
            Map<Long, Product> products = new LinkedHashMap<>();

            brands.put(1L, withId(new Brand("Loopers", "테스트 브랜드"), 1L));
            products.put(1L, withId(new Product(1L, "상품1", "설명1", 1_000L, 10), 1L));
            Product stoppedProduct = withId(new Product(1L, "상품2", "설명2", 2_000L, 10), 2L);
            stoppedProduct.stopSelling();
            products.put(2L, stoppedProduct);

            ProductQueryService service = new ProductQueryService(
                new FakeProductRepository(products),
                new FakeBrandRepository(brands),
                new FakeProductLikeRepository()
            );

            // act
            PageResult<ProductResult> results = service.searchProducts(
                new ProductQuery.AdminSearch(null, 0, 20, "latest")
            );

            // assert
            assertAll(
                () -> assertThat(results.items()).hasSize(2),
                () -> assertThat(results.items()).extracting(ProductResult::status)
                    .containsExactlyInAnyOrder(ProductStatus.ON_SALE, ProductStatus.STOPPED),
                () -> assertThat(results.totalElements()).isEqualTo(2)
            );
        }
    }

    private record LikeKey(String userId, Long productId) {}

    private static class FakeProductLikeRepository implements ProductLikeRepository {
        private final Map<LikeKey, ProductLike> productLikes = new HashMap<>();
        private int findLikedProductIdsCallCount = 0;
        private int existsCallCount = 0;

        @Override
        public ProductLike save(ProductLike productLike) {
            productLikes.put(new LikeKey(productLike.getUserId(), productLike.getProductId()), productLike);
            return productLike;
        }

        @Override
        public boolean saveIfAbsent(ProductLike productLike) {
            LikeKey key = new LikeKey(productLike.getUserId(), productLike.getProductId());
            if (productLikes.containsKey(key)) {
                return false;
            }

            productLikes.put(key, productLike);
            return true;
        }

        @Override
        public Optional<ProductLike> find(String userId, Long productId) {
            return Optional.ofNullable(productLikes.get(new LikeKey(userId, productId)));
        }

        @Override
        public boolean exists(String userId, Long productId) {
            existsCallCount++;
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
            return productLikes.values()
                .stream()
                .filter(productLike -> productLike.getUserId().equals(userId))
                .toList();
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
            return productLikes.values()
                .stream()
                .filter(productLike -> productLike.getUserId().equals(userId))
                .count();
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
            return ids.stream()
                .map(products::get)
                .toList();
        }

        @Override
        public List<Product> findByBrandId(Long brandId) {
            return products.values()
                .stream()
                .filter(product -> product.getBrandId().equals(brandId))
                .toList();
        }

        @Override
        public List<Product> search(ProductSearchCondition condition) {
            return searchableProducts(condition)
                .stream()
                .skip((long) condition.page() * condition.size())
                .limit(condition.size())
                .toList();
        }

        @Override
        public long count(ProductSearchCondition condition) {
            return searchableProducts(condition).size();
        }

        private List<Product> searchableProducts(ProductSearchCondition condition) {
            return products.values()
                .stream()
                .filter(product -> condition.status() == null || product.getStatus() == condition.status())
                .filter(product -> condition.brandId() == null || product.getBrandId().equals(condition.brandId()))
                .toList();
        }

        @Override
        public int increaseLikeCount(Long productId) {
            products.get(productId).increaseLikeCount();
            return 1;
        }

        @Override
        public int decreaseLikeCount(Long productId) {
            products.get(productId).decreaseLikeCount();
            return 1;
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
            return ids.stream()
                .map(brands::get)
                .toList();
        }

        @Override
        public List<Brand> findAll(int page, int size) {
            return brands.values()
                .stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        }

        @Override
        public long countAll() {
            return brands.size();
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
