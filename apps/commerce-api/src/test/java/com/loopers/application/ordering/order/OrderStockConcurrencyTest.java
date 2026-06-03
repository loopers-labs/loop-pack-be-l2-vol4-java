package com.loopers.application.ordering.order;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderStockConcurrencyTest {

    private final OrderFacade orderFacade;
    private final OrderRepository orderRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderStockConcurrencyTest(
        OrderFacade orderFacade,
        OrderRepository orderRepository,
        BrandRepository brandRepository,
        ProductRepository productRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.orderRepository = orderRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 동시에 주문해도 비관적 락으로 재고보다 많은 주문을 생성하지 않는다.")
    @Test
    void preventsOverselling_whenOrdersRequestSameProductConcurrently() throws Exception {
        // arrange
        Product product = saveProduct("락 테스트 상품", 1_000L, 1);
        int requestCount = 2;
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                String userId = "user" + i;
                futures.add(executorService.submit(placeOrderAfterStart(readyLatch, startLatch, product.getId(), userId)));
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

            // act
            startLatch.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            executorService.shutdown();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            Product changedProduct = productRepository.find(product.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertThat(results).containsExactlyInAnyOrder(true, false),
                () -> assertThat(changedProduct.getStockQuantity()).isZero(),
                () -> assertThat(orderRepository.countAll()).isEqualTo(1L)
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    private Callable<Boolean> placeOrderAfterStart(
        CountDownLatch readyLatch,
        CountDownLatch startLatch,
        Long productId,
        String userId
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                orderFacade.placeOrder(new OrderCommand.Create(
                    userId,
                    List.of(new OrderCommand.Item(productId, 1))
                ));
                return true;
            } catch (CoreException e) {
                if (e.getErrorType() != ErrorType.BAD_REQUEST) {
                    throw e;
                }
                return false;
            }
        };
    }

    private Product saveProduct(String name, Long price, Integer stockQuantity) {
        Brand brand = brandRepository.save(new Brand("Loopers", "테스트 브랜드"));
        return productRepository.save(new Product(brand.getId(), name, "설명", price, stockQuantity));
    }
}
