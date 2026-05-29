package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemInput;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductStockServiceIntegrationTest {

    @Autowired private ProductStockService productStockService;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private ProductStockModel stock;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
        stock = productStockRepository.save(new ProductStockModel(product, new Price(10000L), 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 차감 시,")
    @Nested
    class Decrease {

        @DisplayName("재고가 충분하면, 정상적으로 차감된다.")
        @Test
        void decreasesStock_whenStockIsSufficient() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 3));

            productStockService.decrease(inputs);

            ProductStockModel updated = productStockRepository.findById(stock.getId()).get();
            assertThat(updated.getStockQuantity().getValue()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면, 예외가 발생하고 재고는 변하지 않는다.")
        @Test
        void throwsException_whenStockIsInsufficient() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 20));

            try {
                productStockService.decrease(inputs);
            } catch (CoreException ignored) {}

            ProductStockModel unchanged = productStockRepository.findById(stock.getId()).get();
            assertThat(unchanged.getStockQuantity().getValue()).isEqualTo(10);
        }

        @DisplayName("동시에 재고를 초과하는 요청이 들어와도, 재고는 0 미만이 되지 않는다.")
        @Test
        void preventsNegativeStock_whenConcurrentRequestsExceedStock() throws InterruptedException {
            int threadCount = 15;
            ConcurrentResult result = runConcurrent(threadCount, () ->
                    productStockService.decrease(List.of(new OrderItemInput(stock.getId(), 1)))
            );

            ProductStockModel updated = productStockRepository.findById(stock.getId()).get();
            assertThat(result.successCount()).isEqualTo(10);
            assertThat(result.failureCount()).isEqualTo(5);
            assertThat(updated.getStockQuantity().getValue()).isEqualTo(0);
        }
    }

    record ConcurrentResult(int successCount, int failureCount) {}

    private ConcurrentResult runConcurrent(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    task.run();
                    successCount.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        return new ConcurrentResult(successCount.get(), failureCount.get());
    }
}
