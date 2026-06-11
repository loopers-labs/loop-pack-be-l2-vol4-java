package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("재고 차감 동시성")
@SpringBootTest
class OrderConcurrencyIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 N개 상품에 동시 주문 M(>N)건이 각 1개씩 들어오면, 정확히 N건만 성공하고 재고는 0이며 음수가 되지 않는다.")
    @Test
    void concurrentOrders_doNotOversellStock() throws InterruptedException {
        int stock = 100;
        int attempts = 150;
        Long productId = productJpaRepository.save(
                Product.create(brandId, "한정수량상품", Money.of(1_000L))).getId();
        inventoryJpaRepository.save(Inventory.create(productId, stock));

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(attempts);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            final long userId = 1_000L + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    orderApplicationService.place(new OrderCriteria.Place(
                            userId, null, List.of(new OrderCriteria.Line(productId, 1))));
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // 락 경합으로 인한 인프라 예외(낙관적 락 충돌 등)도 실패로 집계
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 주문 시도가 30초 내에 끝나야 한다").isTrue();

        int remaining = inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId).orElseThrow().getQuantity();

        assertThat(successCount.get()).as("성공한 주문 수").isEqualTo(stock);
        assertThat(failureCount.get()).as("실패한 주문 수").isEqualTo(attempts - stock);
        assertThat(remaining).as("최종 재고").isZero();
        assertThat(remaining).as("재고는 음수가 될 수 없다").isGreaterThanOrEqualTo(0);
    }
}
