package com.loopers.domain.stock;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 차감 동시성 — 동시 주문이 같은 상품 재고를 깎을 때 lost update 없이 정합성을 보장하는지 검증한다.
 *
 * - 비관적 락(decrease): SELECT ... FOR UPDATE로 직렬화 → 초기 재고만큼만 성공, 초과는 CONFLICT, 최종 0(음수 없음).
 * - 낙관적 락(decreaseOptimistic): @Version 충돌로 동시 차감 일부가 거부됨 → 성공한 수만큼만 정확히 차감(lost update 없음).
 *
 * (재고는 product와 ID 참조만 하므로 상품 행 없이 stock만 초기화해 서비스 단위로 검증한다.)
 */
@SpringBootTest
public class StockServiceIntegrationTest {

    @Autowired StockService stockService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long PRODUCT_ID = 777L;
    private static final int INITIAL = 10;
    private static final int THREADS = 20;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("비관적 락 — 재고 10개에 20건 동시 차감 시 정확히 10건만 성공하고 최종 재고는 0이다")
    @Test
    void given_stock10_when_20concurrentDecrease_then_exactly10SucceedAndStockIsZero() throws InterruptedException {
        stockService.initialize(PRODUCT_ID, INITIAL);

        Result result = runConcurrentDecrease(false);

        assertThat(result.success.get()).isEqualTo(INITIAL);
        assertThat(result.failure.get()).isEqualTo(THREADS - INITIAL);
        assertThat(stockService.getQuantity(PRODUCT_ID)).isZero();
    }

    @DisplayName("낙관적 락 — 동시 차감해도 성공한 수만큼만 정확히 차감된다(lost update 없음, 음수 없음)")
    @Test
    void given_stock10_when_concurrentOptimisticDecrease_then_noLostUpdate() throws InterruptedException {
        stockService.initialize(PRODUCT_ID, INITIAL);

        Result result = runConcurrentDecrease(true);

        int finalQuantity = stockService.getQuantity(PRODUCT_ID);
        // 충돌로 거부된 만큼은 차감되지 않는다 → 차감된 양 == 성공 건수 (lost update 없음)
        assertThat(finalQuantity).isEqualTo(INITIAL - result.success.get());
        assertThat(finalQuantity).isGreaterThanOrEqualTo(0);
        assertThat(result.success.get()).isLessThanOrEqualTo(INITIAL);
    }

    private Result runConcurrentDecrease(boolean optimistic) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (optimistic) {
                        stockService.decreaseOptimistic(PRODUCT_ID, 1);
                    } else {
                        stockService.decrease(PRODUCT_ID, 1);
                    }
                    success.incrementAndGet();
                } catch (Throwable t) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        return new Result(success, failure);
    }

    private record Result(AtomicInteger success, AtomicInteger failure) {}
}
