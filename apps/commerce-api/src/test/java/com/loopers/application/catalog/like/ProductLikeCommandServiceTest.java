package com.loopers.application.catalog.like;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.like.ProductLike;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.product.ProductStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductLikeCommandServiceTest {

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Like {

        @DisplayName("신규 좋아요이면 이력을 저장하고 상품 좋아요 수를 증가시킨다.")
        @Test
        void savesLikeAndIncreasesLikeCount_whenLikeIsNew() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveOnSaleProduct();

            // act
            ProductLikeResult result = fixture.service.like(new ProductLikeCommand.Like("user1", 1L));

            // assert
            assertAll(
                () -> assertThat(result.liked()).isTrue(),
                () -> assertThat(product.getLikeCount()).isEqualTo(1L),
                () -> assertThat(fixture.productLikeRepository.exists("user1", 1L)).isTrue()
            );
        }

        @DisplayName("이미 좋아요한 상품이면 멱등 성공하고 좋아요 수를 다시 증가시키지 않는다.")
        @Test
        void doesNotIncreaseLikeCount_whenLikeAlreadyExists() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveOnSaleProduct();
            fixture.service.like(new ProductLikeCommand.Like("user1", 1L));

            // act
            ProductLikeResult result = fixture.service.like(new ProductLikeCommand.Like("user1", 1L));

            // assert
            assertAll(
                () -> assertThat(result.liked()).isTrue(),
                () -> assertThat(product.getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("판매 가능 상품이 아니면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIsNotOnSale() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.products.put(1L, new Product(1L, "상품", "설명", 1_000L, 0));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                fixture.service.like(new ProductLikeCommand.Like("user1", 1L));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("기존 좋아요가 있으면 이력을 삭제하고 상품 좋아요 수를 감소시킨다.")
        @Test
        void deletesLikeAndDecreasesLikeCount_whenLikeExists() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveOnSaleProduct();
            fixture.service.like(new ProductLikeCommand.Like("user1", 1L));

            // act
            ProductLikeResult result = fixture.service.unlike(new ProductLikeCommand.Unlike("user1", 1L));

            // assert
            assertAll(
                () -> assertThat(result.liked()).isFalse(),
                () -> assertThat(product.getLikeCount()).isZero(),
                () -> assertThat(fixture.productLikeRepository.exists("user1", 1L)).isFalse()
            );
        }

        @DisplayName("좋아요가 없어도 멱등 성공하고 좋아요 수를 감소시키지 않는다.")
        @Test
        void doesNotDecreaseLikeCount_whenLikeDoesNotExist() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveOnSaleProduct();

            // act
            ProductLikeResult result = fixture.service.unlike(new ProductLikeCommand.Unlike("user1", 1L));

            // assert
            assertAll(
                () -> assertThat(result.liked()).isFalse(),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("판매 중지 상품이어도 기존 좋아요 취소는 허용한다.")
        @Test
        void allowsUnlike_whenProductIsStopped() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveOnSaleProduct();
            fixture.service.like(new ProductLikeCommand.Like("user1", 1L));
            product.stopSelling();

            // act
            ProductLikeResult result = fixture.service.unlike(new ProductLikeCommand.Unlike("user1", 1L));

            // assert
            assertAll(
                () -> assertThat(result.liked()).isFalse(),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }
    }

    private static class TestFixture {
        private final Map<Long, Product> products = new HashMap<>();
        private final Map<Long, Brand> brands = new HashMap<>();
        private final FakeProductLikeRepository productLikeRepository = new FakeProductLikeRepository();
        private final ProductLikeCommandService service = new ProductLikeCommandService(
            productLikeRepository,
            new FakeProductRepository(products),
            new FakeBrandRepository(brands)
        );

        private TestFixture() {
            brands.put(1L, new Brand("Loopers", "테스트 브랜드"));
        }

        private Product saveOnSaleProduct() {
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);
            products.put(1L, product);
            return product;
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
}
