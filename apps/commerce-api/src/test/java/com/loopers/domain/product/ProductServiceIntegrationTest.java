package com.loopers.domain.product;

import com.loopers.application.product.ProductService;
import com.loopers.domain.product.ProductFilter;
import com.loopers.infrastructure.product.ProductJpaRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetAll {

        @DisplayName("삭제된 상품은 목록 조회에서 제외된다.")
        @Test
        void excludesDeletedProducts_whenProductsAreSoftDeleted() {
            // arrange
            ProductModel active = productJpaRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            ProductModel deleted = productJpaRepository.save(new ProductModel("에어맥스90", 159000L, 1L));
            deleted.delete();
            productJpaRepository.save(deleted);

            // act
            Page<ProductModel> result = productService.getAll(ProductFilter.of(null, null, null, null), ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(active.getId());
        }

        @DisplayName("price_asc 정렬이 DB 레벨에서 올바르게 동작한다.")
        @Test
        void returnsProducts_orderedByPriceAsc_atDbLevel() {
            // arrange
            productJpaRepository.save(new ProductModel("상품A", 30000L, 1L));
            productJpaRepository.save(new ProductModel("상품B", 10000L, 1L));
            productJpaRepository.save(new ProductModel("상품C", 20000L, 1L));

            // act
            Page<ProductModel> result = productService.getAll(ProductFilter.of(null, null, null, null), ProductSort.PRICE_ASC, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent().get(0).getPrice()).isEqualTo(10000L);
            assertThat(result.getContent().get(1).getPrice()).isEqualTo(20000L);
            assertThat(result.getContent().get(2).getPrice()).isEqualTo(30000L);
        }

        @DisplayName("brandId 필터가 DB 레벨에서 올바르게 동작한다.")
        @Test
        void returnsProducts_filteredByBrandId_atDbLevel() {
            // arrange
            productJpaRepository.save(new ProductModel("나이키상품", 139000L, 1L));
            productJpaRepository.save(new ProductModel("아디다스상품", 99000L, 2L));

            // act
            Page<ProductModel> result = productService.getAll(ProductFilter.of(1L, null, null, null), ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("나이키상품");
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("삭제된 상품을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsSoftDeleted() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            product.delete();
            productJpaRepository.save(product);

            // act & assert
            assertThatThrownBy(() -> productService.getById(product.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
