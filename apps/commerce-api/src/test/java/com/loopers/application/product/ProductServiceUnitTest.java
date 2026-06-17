package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductLikeCountRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.ProductStock;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceUnitTest {

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(new FakeProductRepository(), new FakeProductStockRepository(), new FakeProductLikeCountRepository());
    }

    static class FakeProductLikeCountRepository implements ProductLikeCountRepository {
        @Override
        public void increment(Long productId) {
        }

        @Override
        public void decrement(Long productId) {
        }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> store = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public Product save(Product product) {
            if (product.getId() == null) {
                Product persisted = new Product(
                    idSequence.getAndIncrement(),
                    product.getBrandId(),
                    product.getName(),
                    product.getPrice(),
                    product.getLikeCount(),
                    ZonedDateTime.now(),
                    ZonedDateTime.now(),
                    product.getDeletedAt()
                );
                store.put(persisted.getId(), persisted);
                return persisted;
            }
            store.put(product.getId(), product);
            return product;
        }

        @Override
        public Optional<Product> find(Long id) {
            return Optional.ofNullable(store.get(id))
                .filter(p -> p.getDeletedAt() == null);
        }

        @Override
        public Page<Product> findAll(Long brandId, Pageable pageable) {
            List<Product> active = store.values().stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> brandId == null || brandId.equals(p.getBrandId()))
                .toList();
            return new PageImpl<>(active, pageable, active.size());
        }

        @Override
        public void deleteAllByBrandId(Long brandId) {
            store.values().stream()
                .filter(p -> brandId.equals(p.getBrandId()))
                .forEach(Product::delete);
        }

        @Override
        public void incrementLikeCount(Long id) {
        }

        @Override
        public void decrementLikeCount(Long id) {
        }

        @Override
        public void adjustLikeCount(Long id, long amount) {
        }
    }

    static class FakeProductStockRepository implements ProductStockRepository {
        private final Map<Long, ProductStock> store = new HashMap<>();

        @Override
        public ProductStock save(ProductStock stock) {
            store.put(stock.getProductId(), stock);
            return stock;
        }

        @Override
        public Optional<ProductStock> findByProductId(Long productId) {
            return Optional.ofNullable(store.get(productId));
        }

        @Override
        public ProductStock findByProductIdForUpdate(Long productId) {
            if (!store.containsKey(productId)) {
                throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다.");
            }
            return store.get(productId);
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID를 주면, 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenValidIdIsProvided() {
            Product created = productService.createProduct(1L, "티셔츠", BigDecimal.valueOf(15000), 10L);

            Product result = productService.getProduct(created.getId());

            assertAll(
                () -> assertThat(result.getId()).isEqualTo(created.getId()),
                () -> assertThat(result.getName()).isEqualTo("티셔츠")
            );
        }

        @DisplayName("존재하지 않는 상품 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> productService.getProduct(9999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 상품 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsSoftDeleted() {
            Product created = productService.createProduct(1L, "티셔츠", BigDecimal.valueOf(15000), 5L);
            productService.deleteProduct(created.getId());

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.getProduct(created.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 정보를 주면, 상품과 재고가 함께 생성된다.")
        @Test
        void createsProductWithStock_whenValidInfoIsProvided() {
            Product product = productService.createProduct(1L, "청바지", BigDecimal.valueOf(50000), 10L);

            ProductStock stock = productService.getProductStock(product.getId());
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("청바지"),
                () -> assertThat(stock.getQuantity()).isEqualTo(10L)
            );
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 정보로 수정하면, 상품과 재고가 함께 수정된다.")
        @Test
        void updatesProductAndStock_whenValidInfoIsProvided() {
            Product product = productService.createProduct(1L, "청바지", BigDecimal.valueOf(50000), 10L);

            productService.updateProduct(product.getId(), 1L, "수정 청바지", BigDecimal.valueOf(45000), 20L);

            Product updated = productService.getProduct(product.getId());
            ProductStock stock = productService.getProductStock(product.getId());
            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("수정 청바지"),
                () -> assertThat(stock.getQuantity()).isEqualTo(20L)
            );
        }

        @DisplayName("존재하지 않는 상품을 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> productService.updateProduct(9999L, 1L, "이름", BigDecimal.valueOf(1000), 5L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {

        @DisplayName("정렬 조건 없이 조회하면, 전체 상품이 반환된다.")
        @Test
        void returnsProducts_orderedByLatest() {
            productService.createProduct(1L, "상품1", BigDecimal.valueOf(10000), 5L);
            productService.createProduct(1L, "상품2", BigDecimal.valueOf(20000), 3L);

            Page<Product> result = productService.getProducts(null, ProductSort.LATEST, Pageable.ofSize(10));

            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 상품을 삭제하면, 삭제된다.")
        @Test
        void softDeletesProduct_whenProductExists() {
            Product product = productService.createProduct(1L, "청바지", BigDecimal.valueOf(50000), 5L);

            productService.deleteProduct(product.getId());

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.getProduct(product.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 삭제된 상품을 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductAlreadyDeleted() {
            Product product = productService.createProduct(1L, "청바지", BigDecimal.valueOf(50000), 5L);
            productService.deleteProduct(product.getId());

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.deleteProduct(product.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}