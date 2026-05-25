package com.loopers.domain.product;

import com.loopers.domain.money.Money;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @DisplayName("상품을 조회할 때,")
    @Nested
    class GetProduct {
        @DisplayName("존재하는 상품 ID를 주면, 해당 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenValidIdIsProvided() {
            // arrange
            Product product = productJpaRepository.save(
                new Product("에어맥스", "편한 러닝화", new Money(BigDecimal.valueOf(100000)), new Stock(10), 1L));

            // act
            Product result = productService.getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getId()).isEqualTo(product.getId()),
                () -> assertThat(result.getName()).isEqualTo(product.getName())
            );
        }

        @DisplayName("존재하지 않는 상품 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidIdIsProvided() {
            // arrange
            Long invalidId = 999L;

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                productService.getProduct(invalidId);
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {
        @DisplayName("latest 정렬이면, 최신 등록순으로 반환한다.")
        @Test
        void returnsProductsInLatestOrder() {
            // arrange
            Product p1 = productJpaRepository.save(new Product("상품1", null, new Money(BigDecimal.valueOf(1000)), new Stock(1), 1L));
            Product p2 = productJpaRepository.save(new Product("상품2", null, new Money(BigDecimal.valueOf(2000)), new Stock(1), 1L));
            Product p3 = productJpaRepository.save(new Product("상품3", null, new Money(BigDecimal.valueOf(3000)), new Stock(1), 1L));

            // act
            List<Product> result = productService.getProducts(null, "latest", 0, 10);

            // assert (최신 등록 = 마지막 저장이 먼저)
            assertThat(result).extracting(Product::getId)
                .containsExactly(p3.getId(), p2.getId(), p1.getId());
        }

        @DisplayName("price_asc 정렬이면, 가격 오름차순으로 반환한다.")
        @Test
        void returnsProductsInPriceAscOrder() {
            // arrange
            Product cheap = productJpaRepository.save(new Product("저가", null, new Money(BigDecimal.valueOf(1000)), new Stock(1), 1L));
            Product mid = productJpaRepository.save(new Product("중가", null, new Money(BigDecimal.valueOf(2000)), new Stock(1), 1L));
            Product expensive = productJpaRepository.save(new Product("고가", null, new Money(BigDecimal.valueOf(3000)), new Stock(1), 1L));

            // act
            List<Product> result = productService.getProducts(null, "price_asc", 0, 10);

            // assert
            assertThat(result).extracting(Product::getId)
                .containsExactly(cheap.getId(), mid.getId(), expensive.getId());
        }

        @DisplayName("brandId를 주면, 해당 브랜드 상품만 반환한다.")
        @Test
        void returnsOnlyProductsOfGivenBrand() {
            // arrange
            Product nikeA = productJpaRepository.save(new Product("나이키A", null, new Money(BigDecimal.valueOf(1000)), new Stock(1), 1L));
            Product nikeB = productJpaRepository.save(new Product("나이키B", null, new Money(BigDecimal.valueOf(2000)), new Stock(1), 1L));
            Product adidas = productJpaRepository.save(new Product("아디다스", null, new Money(BigDecimal.valueOf(3000)), new Stock(1), 2L));

            // act
            List<Product> result = productService.getProducts(1L, "latest", 0, 10);

            // assert
            assertThat(result).extracting(Product::getId).containsExactly(nikeB.getId(), nikeA.getId());
            assertThat(result).extracting(Product::getBrandId).containsOnly(1L);
        }

        @DisplayName("page와 size를 주면, 해당 페이지 조각만 반환한다.")
        @Test
        void returnsRequestedPageSlice() {
            // arrange — 가격 1000~5000 인 5개
            for (int i = 1; i <= 5; i++) {
                productJpaRepository.save(new Product("상품" + i, null, new Money(BigDecimal.valueOf(i * 1000L)), new Stock(1), 1L));
            }

            // act — price_asc(1000..5000) 중 page=1, size=2 → 3·4번째
            List<Product> result = productService.getProducts(null, "price_asc", 1, 2);

            // assert
            assertThat(result).extracting(Product::getName).containsExactly("상품3", "상품4");
        }
    }
}
