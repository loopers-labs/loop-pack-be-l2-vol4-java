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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 재고 차감 동시성 검증. 같은 상품을 여러 요청이 동시에 차감해도 재고만큼만 성공하고 음수가 되지 않아야 한다.
 * decrease는 비관적 락(SELECT ... FOR UPDATE)으로 행을 직렬화해 lost update를 막는다.
 */
@SpringBootTest
class StockConcurrencyIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고보다 많은 요청이 동시에 차감해도 재고만큼만 성공하고 재고는 음수가 되지 않는다")
    @Test
    void decrementsExactlyToStock_withoutGoingNegative() throws InterruptedException {
        // given - 재고 5
        Long productId = 1001L;
        stockRepository.save(new StockModel(productId, 5));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // when - 10개 스레드가 동시에 1개씩 차감
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    stockService.decrease(productId, 1);
                    success.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // then - 5건만 성공, 5건 실패, 재고 0 (음수 없음)
        assertAll(
            () -> assertThat(success.get()).isEqualTo(5),
            () -> assertThat(failure.get()).isEqualTo(5),
            () -> assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity()).isEqualTo(0)
        );
    }
}
