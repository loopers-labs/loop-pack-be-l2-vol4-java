package com.loopers.concurrency;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderCriteria;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
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

/**
 * 프로덕션 주문 경로(InventoryJpaRepository.findAllByProductIdInAndDeletedAtIsNullOrderByProductIdAsc)가
 * 서로 라인 순서가 다른 다중 상품 주문에도 데드락 없이 완료되는지 회귀 검증.
 *
 * 학습용 LockOrderingDeadlockTest 는 행을 '하나씩' 반대로 잠가 일부러 데드락을 만들지만,
 * 실제 주문은 단일 IN 쿼리를 product_id 오름차순으로 잠그므로, 라인 순서가 엇갈려도 같은 순서로 락을 획득한다.
 */
@DisplayName("주문 비관락 — 프로덕션 쿼리의 다중 상품 락 순서")
@SpringBootTest
class OrderLockOrderingIntegrationTest {

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

    private Long productA;
    private Long productB;

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productA = productJpaRepository.save(Product.create(brandId, "상품A", Money.of(1_000L))).getId();
        productB = productJpaRepository.save(Product.create(brandId, "상품B", Money.of(2_000L))).getId();
        inventoryJpaRepository.save(Inventory.create(productA, 1_000));
        inventoryJpaRepository.save(Inventory.create(productB, 1_000));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("두 스레드가 라인 순서를 [A,B]/[B,A]로 엇갈려 동시에 주문해도, 데드락 없이 모두 성공한다.")
    @Test
    void concurrentMultiProductOrders_withDifferentLineOrder_completeWithoutDeadlock() throws InterruptedException {
        int iterations = 50;
        int total = iterations * 2;

        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(total);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < iterations; i++) {
            final long u1 = 1_000L + i;
            final long u2 = 5_000L + i;
            // T1: [A, B] 순서
            executor.submit(() -> runOrder(startGate, success, failure, doneGate, u1,
                    List.of(new OrderCriteria.Line(productA, 1), new OrderCriteria.Line(productB, 1))));
            // T2: [B, A] 반대 순서 — 프로덕션 쿼리는 product_id 오름차순으로 잠가 락 순서가 같다
            executor.submit(() -> runOrder(startGate, success, failure, doneGate, u2,
                    List.of(new OrderCriteria.Line(productB, 1), new OrderCriteria.Line(productA, 1))));
        }

        startGate.countDown();
        boolean finished = doneGate.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).as("엇갈린 순서 주문이 60초 내에 모두 끝나야 한다(데드락 없음)").isTrue();
        assertThat(failure.get()).as("데드락/실패 없이 모두 성공해야 한다").isZero();
        assertThat(success.get()).as("성공 주문 수").isEqualTo(total);
    }

    private void runOrder(CountDownLatch startGate, AtomicInteger success, AtomicInteger failure,
                          CountDownLatch doneGate, long userId, List<OrderCriteria.Line> lines) {
        try {
            startGate.await();
            orderApplicationService.place(new OrderCriteria.Place(userId, null, lines));
            success.incrementAndGet();
        } catch (Exception e) {
            failure.incrementAndGet();
        } finally {
            doneGate.countDown();
        }
    }
}
