package com.loopers.domain.stock;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProductStockServiceIntegrationTest {

    private final ProductStockService productStockService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductStockServiceIntegrationTest(
        ProductStockService productStockService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productStockService = productStockService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 재고를 생성할 때 ")
    @Nested
    class CreateProductStock {

        @DisplayName("같은 상품 ID의 재고를 두 번 생성하면, unique 제약 예외가 발생한다.")
        @Test
        void throwsDataIntegrityViolation_whenProductStockAlreadyExists() {
            // arrange
            Long productId = 101L;
            productStockService.createProductStock(productId, 10);

            // act & assert
            assertThatThrownBy(() -> productStockService.createProductStock(productId, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @DisplayName("상품 재고를 lock 조회할 때 ")
    @Nested
    class GetProductStocksForUpdate {

        @DisplayName("여러 상품 ID가 주어지면, 상품 ID 오름차순으로 재고를 조회한다.")
        @Test
        void returnsProductStocksByProductIdAsc_whenProductIdsAreProvided() {
            // arrange
            productStockService.createProductStock(103L, 10);
            productStockService.createProductStock(101L, 10);
            productStockService.createProductStock(102L, 10);

            // act
            List<ProductStock> productStocks = productStockService.getProductStocksForUpdate(List.of(103L, 101L, 102L));

            // assert
            assertThat(productStocks)
                .extracting(ProductStock::getProductId)
                .containsExactly(101L, 102L, 103L);
        }
    }
}
