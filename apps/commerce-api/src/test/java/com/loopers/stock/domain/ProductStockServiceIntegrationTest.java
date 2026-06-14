package com.loopers.stock.domain;

import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

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

    @DisplayName("재고를 동시에 차감할 때 ")
    @Nested
    class Deduct {

        @DisplayName("재고보다 많은 주문이 동시에 차감을 요청하면, 재고 수량만큼만 성공하고 재고는 0이 된다.")
        @Test
        void deductsOnlyUpToStock_whenConcurrentRequestsExceedStock() throws InterruptedException {
            // arrange
            Long productId = 101L;
            int initialStock = 5;
            int concurrentRequests = 10;
            productStockService.createProductStock(productId, initialStock);
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();

            // act
            for (int i = 0; i < concurrentRequests; i++) {
                executor.submit(() -> {
                    try {
                        productStockService.deduct(Map.of(productId, 1));
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            int remaining = productStockService.getProductStock(productId).getQuantity();
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(initialStock),
                () -> assertThat(failureCount.get()).isEqualTo(concurrentRequests - initialStock),
                () -> assertThat(remaining).isZero()
            );
        }

        @DisplayName("재고 내에서 여러 주문이 동시에 차감을 요청하면, 모두 성공하고 재고가 정확히 차감된다.")
        @Test
        void deductsAccurately_whenConcurrentRequestsAreWithinStock() throws InterruptedException {
            // arrange
            Long productId = 101L;
            int initialStock = 100;
            int concurrentRequests = 50;
            productStockService.createProductStock(productId, initialStock);
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            AtomicInteger successCount = new AtomicInteger();

            // act
            for (int i = 0; i < concurrentRequests; i++) {
                executor.submit(() -> {
                    try {
                        productStockService.deduct(Map.of(productId, 1));
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            int remaining = productStockService.getProductStock(productId).getQuantity();
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(concurrentRequests),
                () -> assertThat(remaining).isEqualTo(initialStock - concurrentRequests)
            );
        }
    }
}
