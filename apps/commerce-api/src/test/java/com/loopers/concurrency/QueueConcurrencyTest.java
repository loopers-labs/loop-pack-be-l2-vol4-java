package com.loopers.concurrency;

import com.loopers.application.queue.EntryTokenScheduler;
import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueuePolicy;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 백그라운드 스케줄러가 끼어들면 발급 수량 검증이 비결정적이 되므로 비활성화하고,
// 스케줄러 로직은 직접 인스턴스를 만들어 원하는 시점에 호출한다
@SpringBootTest(properties = "queue.scheduler.enabled=false")
class QueueConcurrencyTest {

    @Autowired private QueueFacade queueFacade;
    @Autowired private WaitingQueueRepository waitingQueueRepository;
    @Autowired private EntryTokenRepository entryTokenRepository;
    @Autowired private RedisCleanUp redisCleanUp;

    private EntryTokenScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EntryTokenScheduler(waitingQueueRepository, entryTokenRepository);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void enterConcurrently(int userCount) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger();

        for (long userId = 1; userId <= userCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    queueFacade.enter(uid);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "동시 진입이 10초 내에 끝나야 한다");
        executor.shutdown();
        assertThat(successCount.get()).isEqualTo(userCount);
    }

    @DisplayName("여러 유저가 동시에 진입해도 유실·중복 없이 1..N 순번이 정확히 부여된다.")
    @Test
    void concurrentEnter_assignsUniquePositionsWithoutLoss() throws InterruptedException {
        // arrange & act
        int userCount = 20;
        enterConcurrently(userCount);

        // assert — 순번이 1..N을 정확히 한 번씩 커버 (유실 없음, 중복 순번 없음)
        Set<Long> positions = new HashSet<>();
        for (long uid = 1; uid <= userCount; uid++) {
            positions.add(queueFacade.getPosition(uid).position());
        }
        assertAll(
            () -> assertThat(waitingQueueRepository.countWaiting()).isEqualTo(userCount),
            () -> assertThat(positions).hasSize(userCount),
            () -> assertThat(positions).allMatch(p -> p >= 1 && p <= userCount)
        );
    }

    @DisplayName("배치 크기 이상의 유저가 몰려도 스케줄러는 배치 크기만큼만 입장시키고 나머지는 대기 상태를 유지한다.")
    @Test
    void schedulerCapsThroughputAtBatchSize_whenDemandExceedsCapacity() throws InterruptedException {
        // arrange — 배치 크기(30)를 넘는 50명이 동시에 진입
        int overflow = 20;
        int userCount = QueuePolicy.BATCH_SIZE + overflow;
        enterConcurrently(userCount);

        // act — 스케줄러 1회 실행
        scheduler.issueEntryTokens();

        // assert — 정확히 배치 크기만큼만 토큰 발급, 나머지는 대기 유지, 두 집합은 배타적
        int tokenHolders = 0;
        for (long uid = 1; uid <= userCount; uid++) {
            boolean hasToken = entryTokenRepository.find(uid).isPresent();
            boolean isWaiting = waitingQueueRepository.findRank(uid).isPresent();
            assertTrue(hasToken ^ isWaiting, "유저 " + uid + "는 토큰 보유와 대기 중 정확히 하나여야 한다");
            if (hasToken) tokenHolders++;
        }
        final int firstBatchTokenHolders = tokenHolders;
        assertAll(
            () -> assertThat(firstBatchTokenHolders).isEqualTo(QueuePolicy.BATCH_SIZE),
            () -> assertThat(waitingQueueRepository.countWaiting()).isEqualTo(overflow)
        );

        // act — 스케줄러 2회차 실행 시 나머지 전원 입장
        scheduler.issueEntryTokens();

        // assert
        for (long uid = 1; uid <= userCount; uid++) {
            assertThat(entryTokenRepository.find(uid)).isPresent();
        }
        assertThat(waitingQueueRepository.countWaiting()).isEqualTo(0L);
    }

    @DisplayName("진입과 토큰 발급이 동시에 실행되어도 유저가 유실되지 않는다.")
    @Test
    void noUserLost_whenEnterAndIssueRunConcurrently() throws InterruptedException {
        // arrange
        int userCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(userCount + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount + 1);

        // 발급 스레드 — 유저들이 진입하는 동안 반복적으로 대기열을 pop
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    scheduler.issueEntryTokens();
                    Thread.sleep(5);
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        for (long userId = 1; userId <= userCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    queueFacade.enter(uid);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // act
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "진입과 발급이 10초 내에 끝나야 한다");
        executor.shutdown();

        // 잔여 대기 인원 소진
        while (waitingQueueRepository.countWaiting() > 0) {
            scheduler.issueEntryTokens();
        }

        // assert — 전원이 정확히 토큰을 받은 상태로 종료 (pop과 진입이 겹쳐도 유실 없음)
        for (long uid = 1; uid <= userCount; uid++) {
            assertThat(entryTokenRepository.find(uid)).isPresent();
        }
        assertThat(waitingQueueRepository.countWaiting()).isEqualTo(0L);
    }
}
