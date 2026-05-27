package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비관 락(PESSIMISTIC_WRITE) 동작 검증 — Testcontainers MySQL InnoDB 행 락 실측.
 * <p>락이 없다면 read-modify-write 사이 race로 lost update가 발생해 최종 수량이 기대치보다 크다.
 * 락이 있으면 각 차감이 직렬화되어 최종 수량이 결정적.</p>
 */
@SpringBootTest
class StockServiceConcurrencyIntegrationTest {

    private static final Long PRODUCT_ID = 9001L;

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

    @DisplayName("동시 차감 시")
    @org.junit.jupiter.api.Nested
    class ConcurrentDecrease {

        @BeforeEach
        void setUp() {
            stockRepository.save(new StockModel(PRODUCT_ID, 100));
        }

        @DisplayName("재고가 충분한 상황에서 N개 스레드가 동시에 1씩 차감하면 최종 수량은 (초기 - N) 으로 결정적이다")
        @Test
        void decrementsExactly_whenStockIsSufficient() throws Exception {
            // given
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        stockService.decrease(PRODUCT_ID, 1);
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                        // lock timeout / conflict 등은 본 시나리오에서 발생하지 않아야 한다
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            boolean finished = doneGate.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(finished).as("모든 스레드가 시간 내 완료").isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity())
                .isEqualTo(100 - threadCount);
        }
    }

    @DisplayName("재고 부족 경계에서 동시 차감 시")
    @org.junit.jupiter.api.Nested
    class ConcurrentDecreaseOnBoundary {

        @BeforeEach
        void setUp() {
            stockRepository.save(new StockModel(PRODUCT_ID, 3));
        }

        @DisplayName("재고 3개에 10개 스레드가 동시에 1씩 차감하면 정확히 3개만 성공하고 나머지는 CONFLICT, 최종 수량은 0이다")
        @Test
        void onlySufficientThreadsSucceed_whenStockIsTight() throws Exception {
            // given
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger conflictCount = new AtomicInteger();

            // when
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startGate.await();
                        stockService.decrease(PRODUCT_ID, 1);
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.CONFLICT) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            startGate.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
            executor.shutdown();

            // then
            assertThat(successCount.get()).as("재고 수량만큼만 성공").isEqualTo(3);
            assertThat(conflictCount.get()).as("나머지는 모두 CONFLICT").isEqualTo(7);
            assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isZero();
        }
    }
}
