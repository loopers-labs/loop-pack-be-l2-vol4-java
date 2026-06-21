package com.loopers.application.catalog.like;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductLikeQueryServiceTest {

    @DisplayName("내 좋아요 목록을 조회할 때, ")
    @Nested
    class GetMyLikes {

        @DisplayName("판매 중지 상품도 좋아요 이력이 있으면 포함한다.")
        @Test
        void includesStoppedProduct_whenLikeHistoryExists() {
            // arrange
            Map<Long, Brand> brands = new HashMap<>();
            Map<Long, Product> products = new HashMap<>();
            FakeProductLikeRepository productLikeRepository = new FakeProductLikeRepository();

            brands.put(1L, withId(new Brand("Loopers", "테스트 브랜드"), 1L));
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);
            withId(product, 1L);
            product.stopSelling();
            products.put(1L, product);
            productLikeRepository.save(new ProductLike("user1", 1L));

            ProductLikeQueryService service = new ProductLikeQueryService(
                productLikeRepository,
                new FakeProductRepository(products),
                new FakeBrandRepository(brands)
            );

            // act
            PageResult<ProductLikeResult> result = service.getMyLikes(new ProductLikeQuery.MyLikes("user1", 0, 20));

            // assert
            assertAll(
                () -> assertThat(result.totalElements()).isEqualTo(1L),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).status()).isEqualTo(ProductStatus.STOPPED),
                () -> assertThat(result.items().get(0).liked()).isTrue()
            );
        }
    }

    private record LikeKey(String userId, Long productId) {}

    private static class FakeProductLikeRepository implements ProductLikeRepository {
        private final Map<LikeKey, ProductLike> productLikes = new HashMap<>();

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
            return productIds.stream()
                .filter(productId -> exists(userId, productId))
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
            return products.values()
                .stream()
                .filter(product -> condition.status() == null || product.getStatus() == condition.status())
                .filter(product -> condition.brandId() == null || product.getBrandId().equals(condition.brandId()))
                .toList();
        }

        @Override
        public long count(ProductSearchCondition condition) {
            return search(condition).size();
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
