package com.loopers.application.catalog.brand;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.application.catalog.product.ProductCacheRepository;
import com.loopers.application.catalog.product.ProductResult;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.product.ProductStatus;
import com.loopers.support.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandCommandServiceTest {

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("브랜드를 soft delete 처리하고 해당 브랜드 상품을 판매 중지한다.")
        @Test
        void stopsProducts_whenBrandIsDeleted() {
            // arrange
            FakeBrandRepository brandRepository = new FakeBrandRepository();
            FakeProductRepository productRepository = new FakeProductRepository();
            FakeProductCacheRepository productCacheRepository = new FakeProductCacheRepository();
            BrandCommandService service = new BrandCommandService(brandRepository, productRepository, productCacheRepository);

            Long brandId = 1L;
            Brand brand = new Brand("Loopers", "테스트 브랜드");
            Product product = new Product(brandId, "상품", "설명", 1_000L, 10);

            brandRepository.brands.put(brandId, brand);
            productRepository.products.put(1L, product);

            // act
            service.delete(brandId);

            // assert
            assertAll(
                () -> assertThat(brand.isActive()).isFalse(),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED),
                () -> assertThat(product.getDeletedAt()).isNotNull(),
                () -> assertThat(productCacheRepository.evictedDetailProductIds).containsExactly(product.getId()),
                () -> assertThat(productCacheRepository.evictListsCallCount).isEqualTo(1)
            );
        }
    }

    private static class FakeBrandRepository implements BrandRepository {
        private final Map<Long, Brand> brands = new HashMap<>();

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

    private static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> products = new HashMap<>();

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

    private static class FakeProductCacheRepository implements ProductCacheRepository {
        private final List<Long> evictedDetailProductIds = new ArrayList<>();
        private int evictListsCallCount = 0;

        @Override
        public Optional<ProductResult> getDetail(Long productId) {
            return Optional.empty();
        }

        @Override
        public void putDetail(Long productId, ProductResult product) {
        }

        @Override
        public Optional<PageResult<ProductResult>> getList(ProductSearchCondition condition) {
            return Optional.empty();
        }

        @Override
        public void putList(ProductSearchCondition condition, PageResult<ProductResult> products) {
        }

        @Override
        public void evictDetail(Long productId) {
            evictedDetailProductIds.add(productId);
        }

        @Override
        public void evictLists() {
            evictListsCallCount++;
        }
    }
}
