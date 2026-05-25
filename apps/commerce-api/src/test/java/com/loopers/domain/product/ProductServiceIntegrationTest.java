package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.ProductSort;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID를 주면, 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenValidIdIsProvided() {
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
            Product saved = productJpaRepository.save(
                new Product(brand.getId(), "티셔츠", BigDecimal.valueOf(15000))
            );

            Product result = productService.getProduct(saved.getId());

            assertAll(
                () -> assertThat(result.getId()).isEqualTo(saved.getId()),
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
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
            Product saved = productJpaRepository.save(
                new Product(brand.getId(), "티셔츠", BigDecimal.valueOf(15000))
            );
            saved.delete();
            productJpaRepository.save(saved);

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.getProduct(saved.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 정보를 주면, 상품과 재고가 함께 생성된다.")
        @Test
        void createsProductWithStock_whenValidInfoIsProvided() {
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));

            Product product = productService.createProduct(
                brand.getId(), "청바지", BigDecimal.valueOf(50000), 10L);

            ProductStock stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(brand.getId()),
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
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
            Product product = productService.createProduct(
                brand.getId(), "청바지", BigDecimal.valueOf(50000), 10L);

            productService.updateProduct(product.getId(), brand.getId(), "수정 청바지", BigDecimal.valueOf(45000), 20L);

            ProductStock stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
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

        @DisplayName("정렬 조건 없이 조회하면, 최신순으로 반환된다.")
        @Test
        void returnsProducts_orderedByLatest() {
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
            productJpaRepository.save(new Product(brand.getId(), "상품1", BigDecimal.valueOf(10000)));
            productJpaRepository.save(new Product(brand.getId(), "상품2", BigDecimal.valueOf(20000)));

            Page<Product> result = productService.getProducts(null, ProductSort.LATEST, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 상품을 삭제하면, 삭제된다.")
        @Test
        void softDeletesProduct_whenProductExists() {
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
            Product product = productService.createProduct(
                brand.getId(), "청바지", BigDecimal.valueOf(50000), 5L);

            productService.deleteProduct(product.getId());

            Product deleted = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 상품을 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductAlreadyDeleted() {
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
            Product product = productService.createProduct(
                brand.getId(), "청바지", BigDecimal.valueOf(50000), 5L);
            productService.deleteProduct(product.getId());

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.deleteProduct(product.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
