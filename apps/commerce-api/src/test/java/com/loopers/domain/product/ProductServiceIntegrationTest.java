package com.loopers.domain.product;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel saveProduct(Long brandId, String name, Long price) {
        return productRepository.save(ProductModel.of(
                brandId,
                ProductName.of(name),
                ProductDescription.of(name + " 설명"),
                ProductPrice.of(price)
        ));
    }

    @DisplayName("상품 단건 조회할 때")
    @Nested
    class GetProduct {

        @DisplayName("존재한다면, 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenExists() {
            // given
            ProductModel saved = saveProduct(1L, "티셔츠", 10000L);

            // when
            ProductModel result = productService.getProduct(saved.getId());

            // then
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName().value()).isEqualTo("티셔츠");
            assertThat(result.getPrice().value()).isEqualTo(10000L);
        }

        @DisplayName("존재하지 않으면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productService.getProduct(99999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 상품이면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenSoftDeleted() {
            // given
            ProductModel saved = saveProduct(1L, "티셔츠", 10000L);
            saved.delete();
            productRepository.save(saved);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productService.getProduct(saved.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록 조회할 때")
    @Nested
    class GetProducts {

        @DisplayName("sort가 null이면, 최신순으로 반환한다.")
        @Test
        void returnsLatestFirst_whenSortIsNull() {
            // given
            saveProduct(1L, "A", 1000L);
            saveProduct(1L, "B", 2000L);
            ProductModel last = saveProduct(1L, "C", 3000L);

            // when
            Page<ProductModel> result = productService.getProducts(null, null, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getId()).isEqualTo(last.getId());
        }

        @DisplayName("sort가 PRICE_ASC면, 가격 오름차순으로 반환한다.")
        @Test
        void returnsByPriceAsc() {
            // given
            saveProduct(1L, "B", 2000L);
            saveProduct(1L, "C", 3000L);
            saveProduct(1L, "A", 1000L);

            // when
            Page<ProductModel> result = productService.getProducts(null, ProductSortType.PRICE_ASC, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).extracting(p -> p.getPrice().value())
                    .containsExactly(1000L, 2000L, 3000L);
        }

        @DisplayName("sort가 PRICE_DESC면, 가격 내림차순으로 반환한다.")
        @Test
        void returnsByPriceDesc() {
            // given
            saveProduct(1L, "B", 2000L);
            saveProduct(1L, "C", 3000L);
            saveProduct(1L, "A", 1000L);

            // when
            Page<ProductModel> result = productService.getProducts(null, ProductSortType.PRICE_DESC, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).extracting(p -> p.getPrice().value())
                    .containsExactly(3000L, 2000L, 1000L);
        }

        @DisplayName("brandId가 있으면, 해당 브랜드 상품만 반환한다.")
        @Test
        void filtersByBrandId() {
            // given
            saveProduct(1L, "B1상품A", 1000L);
            saveProduct(1L, "B1상품B", 2000L);
            saveProduct(2L, "B2상품A", 3000L);

            // when
            Page<ProductModel> result = productService.getProducts(2L, null, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getBrandId()).isEqualTo(2L);
        }

        @DisplayName("삭제된 상품은 목록에서 제외한다.")
        @Test
        void excludesSoftDeleted() {
            // given
            saveProduct(1L, "유지", 1000L);
            ProductModel deleted = saveProduct(1L, "삭제", 2000L);
            deleted.delete();
            productRepository.save(deleted);

            // when
            Page<ProductModel> result = productService.getProducts(null, null, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName().value()).isEqualTo("유지");
        }
    }
}